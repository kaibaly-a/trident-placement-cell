package com.trident.placement.service;

import com.trident.placement.dto.admin.*;
import com.trident.placement.entity.*;
import com.trident.placement.enums.NotificationType;
import com.trident.placement.enums.ShortlistStatus;
import com.trident.placement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShortlistServiceImpl implements ShortlistService {

    private final RoundShortlistRepository       roundShortlistRepository;
    private final ShortlistNotificationRepository notificationRepository;
    private final AdminApplicationRepository     applicationRepository;
    private final AdminDriveRepository           driveRepository;
    private final StudentRepository              studentRepository;
    private final DriveJDRepository              driveJDRepository;
    private final AdminRepository                adminRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final int MAX_BULK_SIZE = 1000;

    // ── 1. Get Status ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ShortlistStatusResponse getStatus(Long driveId, String roundName,
                                              String status, int page, int size) {
        Drive drive = findDriveOrThrow(driveId);
        List<String> availableRounds = getAvailableRounds(driveId);

        // Fetch all applications for this drive
        List<Application> applications = applicationRepository.findByDriveId(driveId);

        // Fetch all shortlist records for this drive in one query
        List<RoundShortlist> allShortlists = roundShortlistRepository.findByDriveId(driveId);

        // Build map: applicationId → (round → status)
        Map<Long, Map<String, String>> statusMap = new HashMap<>();
        for (RoundShortlist rs : allShortlists) {
            statusMap
                .computeIfAbsent(rs.getApplication().getId(), k -> new LinkedHashMap<>())
                .put(rs.getRound(), rs.getStatus().name());
        }

        // Filter applications if round + status filters are provided
        List<Application> filtered = applications.stream()
            .filter(app -> {
                if (roundName != null && !roundName.isBlank() && status != null && !status.isBlank()) {
                    Map<String, String> rounds = statusMap.getOrDefault(app.getId(), Map.of());
                    return status.equals(rounds.get(roundName));
                }
                if (roundName != null && !roundName.isBlank()) {
                    return statusMap.getOrDefault(app.getId(), Map.of()).containsKey(roundName);
                }
                if (status != null && !status.isBlank()) {
                    return statusMap.getOrDefault(app.getId(), Map.of()).containsValue(status);
                }
                return true;
            })
            .collect(Collectors.toList());

        long total = filtered.size();
        int clampedSize = size > 0 ? size : 50;
        int offset = page * clampedSize;
        List<Application> pageSlice = filtered.stream()
            .skip(offset)
            .limit(clampedSize)
            .collect(Collectors.toList());

        // Build student info lookup
        Set<String> regdnos = pageSlice.stream()
            .map(Application::getRegdno)
            .collect(Collectors.toSet());
        Map<String, Student> studentMap = studentRepository.findAll().stream()
            .filter(s -> regdnos.contains(s.getRegdno()))
            .collect(Collectors.toMap(Student::getRegdno, s -> s, (a, b) -> a));

        // Build per-student row — pre-populate all available rounds with null
        List<StudentRoundStatusDTO> rows = pageSlice.stream().map(app -> {
            Map<String, String> roundStatus = new LinkedHashMap<>();
            for (String r : availableRounds) {
                roundStatus.put(r, null); // not yet decided
            }
            // Overlay actual statuses
            Map<String, String> actual = statusMap.getOrDefault(app.getId(), Map.of());
            roundStatus.putAll(actual);

            Student student = studentMap.get(app.getRegdno());
            return StudentRoundStatusDTO.builder()
                .applicationId(app.getId())
                .regdno(app.getRegdno())
                .studentName(student != null ? student.getName() : app.getRegdno())
                .studentEmail(student != null ? student.getEmail() : "")
                .branch(student != null ? student.getBranchCode() : "")
                .appliedDate(app.getAppliedDate() != null ? app.getAppliedDate().format(DATE_FMT) : "")
                .roundStatus(roundStatus)
                .build();
        }).collect(Collectors.toList());

        int totalPages = clampedSize > 0 ? (int) Math.ceil((double) total / clampedSize) : 1;

        return ShortlistStatusResponse.builder()
            .data(rows)
            .total(total)
            .page(page)
            .perPage(clampedSize)
            .totalPages(totalPages)
            .availableRounds(availableRounds)
            .build();
    }

    // ── 2. Update Single ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public ShortlistUpdateResponse updateSingle(Long driveId, Long applicationId,
                                                 ShortlistUpdateRequest req,
                                                 String updatedByEmail) {
        Drive drive = findDriveOrThrow(driveId);
        Application application = findApplicationOrThrow(applicationId);

        // Validate round name
        String roundName = req.getRoundName().trim().toUpperCase().replace(" ", "_");
        validateRound(driveId, roundName);

        ShortlistStatus newStatus = ShortlistStatus.valueOf(req.getStatus());
        Admin admin = adminRepository.findByEmailIgnoreCase(updatedByEmail).orElse(null);

        // Upsert — insert or update existing row
        RoundShortlist record = roundShortlistRepository
            .findByApplicationIdAndRound(applicationId, roundName)
            .orElseGet(() -> RoundShortlist.builder()
                .drive(drive)
                .application(application)
                .round(roundName)
                .build());

        record.setStatus(newStatus);
        record.setRemarks(req.getRemarks());
        record.setUpdatedBy(admin);
        record.setDecidedAt(LocalDateTime.now());

        RoundShortlist saved = roundShortlistRepository.save(record);
        log.info("Shortlist updated: drive={} app={} round={} status={} by={}",
            driveId, applicationId, roundName, newStatus, updatedByEmail);

        boolean notifSent = false;
        if (req.isNotify() && newStatus != ShortlistStatus.PENDING) {
            notifSent = sendInAppNotification(application, roundName, newStatus);
        }

        return ShortlistUpdateResponse.builder()
            .id(saved.getId())
            .applicationId(applicationId)
            .regdno(application.getRegdno())
            .round(roundName)
            .status(newStatus.name())
            .remarks(req.getRemarks())
            .decidedAt(saved.getDecidedAt())
            .updatedAt(saved.getUpdatedAt())
            .notificationSent(notifSent)
            .build();
    }

    // ── 3. Bulk Update ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public BulkShortlistResult updateBulk(Long driveId, BulkShortlistUpdateRequest req,
                                           String updatedByEmail) {
        Drive drive = findDriveOrThrow(driveId);

        if (req.getApplicationIds().size() > MAX_BULK_SIZE) {
            throw new IllegalArgumentException(
                "Bulk update limited to " + MAX_BULK_SIZE + " records per request.");
        }

        String roundName = req.getRoundName().trim().toUpperCase().replace(" ", "_");
        validateRound(driveId, roundName);

        ShortlistStatus newStatus = ShortlistStatus.valueOf(req.getStatus());
        Admin admin = adminRepository.findByEmailIgnoreCase(updatedByEmail).orElse(null);

        int updated = 0, failed = 0, notifSent = 0, notifFailed = 0;
        List<BulkShortlistResult.FailedRecord> failedRecords = new ArrayList<>();

        for (Long appId : req.getApplicationIds()) {
            try {
                Application application = applicationRepository.findById(appId)
                    .orElseThrow(() -> new RuntimeException("Application not found: " + appId));

                // Must belong to this drive
                if (!application.getDrive().getId().equals(driveId)) {
                    throw new RuntimeException("Application " + appId + " does not belong to drive " + driveId);
                }

                RoundShortlist record = roundShortlistRepository
                    .findByApplicationIdAndRound(appId, roundName)
                    .orElseGet(() -> RoundShortlist.builder()
                        .drive(drive)
                        .application(application)
                        .round(roundName)
                        .build());

                record.setStatus(newStatus);
                record.setRemarks(req.getRemarks());
                record.setUpdatedBy(admin);
                record.setDecidedAt(LocalDateTime.now());
                roundShortlistRepository.save(record);
                updated++;

                if (req.isNotify() && newStatus != ShortlistStatus.PENDING) {
                    boolean sent = sendInAppNotification(application, roundName, newStatus);
                    if (sent) notifSent++; else notifFailed++;
                }

            } catch (Exception e) {
                log.warn("Bulk update failed for applicationId={}: {}", appId, e.getMessage());
                failed++;
                failedRecords.add(BulkShortlistResult.FailedRecord.builder()
                    .applicationId(appId)
                    .reason(e.getMessage())
                    .build());
            }
        }

        log.info("Bulk shortlist update: drive={} round={} status={} updated={} failed={}",
            driveId, roundName, newStatus, updated, failed);

        return BulkShortlistResult.builder()
            .success(failed == 0)
            .updated(updated)
            .failed(failed)
            .notificationsSent(notifSent)
            .notificationsFailed(notifFailed)
            .failedRecords(failedRecords)
            .build();
    }

    @Override
    @Transactional
    public BulkShortlistResult sendBulkNotifications(Long driveId, com.trident.placement.dto.admin.BulkShortlistNotifyRequest req) {
        findDriveOrThrow(driveId);

        if (req.getApplicationIds().size() > MAX_BULK_SIZE) {
            throw new IllegalArgumentException(
                "Bulk notify limited to " + MAX_BULK_SIZE + " records per request.");
        }

        String roundName = req.getRoundName().trim().toUpperCase().replace(" ", "_");
        validateRound(driveId, roundName);

        int updated = 0, failed = 0, notifSent = 0, notifFailed = 0;
        List<BulkShortlistResult.FailedRecord> failedRecords = new ArrayList<>();

        for (Long appId : req.getApplicationIds()) {
            try {
                Application application = applicationRepository.findById(appId)
                    .orElseThrow(() -> new RuntimeException("Application not found: " + appId));

                if (!application.getDrive().getId().equals(driveId)) {
                    throw new RuntimeException("Application " + appId + " does not belong to drive " + driveId);
                }

                RoundShortlist record = roundShortlistRepository
                    .findByApplicationIdAndRound(appId, roundName)
                    .orElseThrow(() -> new RuntimeException("No decision recorded for this application in round: " + roundName));

                ShortlistStatus status = record.getStatus();
                if (status == ShortlistStatus.PENDING) {
                    throw new RuntimeException("Cannot notify PENDING status");
                }

                boolean sent = sendInAppNotification(application, roundName, status);
                if (sent) {
                    notifSent++;
                } else {
                    notifFailed++; // Already notified
                }
                updated++;
            } catch (Exception e) {
                log.warn("Bulk notify failed for applicationId={}: {}", appId, e.getMessage());
                failed++;
                failedRecords.add(BulkShortlistResult.FailedRecord.builder()
                    .applicationId(appId)
                    .reason(e.getMessage())
                    .build());
            }
        }

        log.info("Bulk shortlist notify: drive={} round={} requested={} sent={} failed={}",
            driveId, roundName, req.getApplicationIds().size(), notifSent, failed);

        return BulkShortlistResult.builder()
            .success(failed == 0)
            .updated(updated)
            .failed(failed)
            .notificationsSent(notifSent)
            .notificationsFailed(notifFailed)
            .failedRecords(failedRecords)
            .build();
    }

    // ── 4. Summary ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ShortlistSummaryResponse getSummary(Long driveId) {
        Drive drive = findDriveOrThrow(driveId);
        List<String> availableRounds = getAvailableRounds(driveId);
        long totalApps = roundShortlistRepository.countTotalApplicationsByDriveId(driveId);

        // Object[] { round, status, count }
        List<Object[]> rawCounts = roundShortlistRepository.countByDriveIdGroupByRoundAndStatus(driveId);

        // Build nested map: round → status → count
        Map<String, Map<ShortlistStatus, Long>> roundStatusCount = new LinkedHashMap<>();
        for (Object[] row : rawCounts) {
            String round   = (String) row[0];
            ShortlistStatus st = (ShortlistStatus) row[1];
            Long count     = (Long) row[2];
            roundStatusCount
                .computeIfAbsent(round, k -> new EnumMap<>(ShortlistStatus.class))
                .put(st, count);
        }

        // Build byRound map, including rounds from JD that have no data yet
        Map<String, ShortlistSummaryResponse.RoundStats> byRound = new LinkedHashMap<>();
        for (String r : availableRounds) {
            Map<ShortlistStatus, Long> counts = roundStatusCount.getOrDefault(r, Map.of());
            long passed  = counts.getOrDefault(ShortlistStatus.PASSED,  0L);
            long failed  = counts.getOrDefault(ShortlistStatus.FAILED,  0L);
            long pending = counts.getOrDefault(ShortlistStatus.PENDING, 0L);
            long decided = passed + failed;
            double passRate = decided > 0 ? Math.round((passed * 1000.0 / decided)) / 10.0 : 0.0;
            byRound.put(r, ShortlistSummaryResponse.RoundStats.builder()
                .passed(passed).failed(failed).pending(pending).passRate(passRate)
                .build());
        }

        return ShortlistSummaryResponse.builder()
            .driveId(driveId)
            .companyName(drive.getCompanyName())
            .totalApplications(totalApps)
            .availableRounds(availableRounds)
            .byRound(byRound)
            .build();
    }

    // ── 5. CSV Export ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCsv(Long driveId, String roundFilter, String statusFilter) {
        findDriveOrThrow(driveId);

        // Fetch records
        List<RoundShortlist> records;
        if (roundFilter != null && !roundFilter.isBlank()) {
            String round = roundFilter.trim().toUpperCase().replace(" ", "_");
            if (statusFilter != null && !statusFilter.isBlank()) {
                records = roundShortlistRepository.findByDriveIdAndRoundAndStatus(
                    driveId, round, ShortlistStatus.valueOf(statusFilter.toUpperCase()));
            } else {
                records = roundShortlistRepository.findByDriveIdAndRound(driveId, round);
            }
        } else {
            records = roundShortlistRepository.findByDriveId(driveId);
        }

        // Fetch student info for regdnos in records
        Set<String> regdnos = records.stream()
            .map(rs -> rs.getApplication().getRegdno())
            .collect(Collectors.toSet());
        Map<String, Student> studentMap = studentRepository.findAll().stream()
            .filter(s -> regdnos.contains(s.getRegdno()))
            .collect(Collectors.toMap(Student::getRegdno, s -> s, (a, b) -> a));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            // BOM for Excel compatibility — ByteArrayOutputStream never actually throws IOException
            try { baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); } catch (java.io.IOException ignored) {}
            pw.println("Application ID,Regdno,Student Name,Branch,Round,Status,Comment,Decided At");
            for (RoundShortlist rs : records) {
                Application app     = rs.getApplication();
                Student student     = studentMap.get(app.getRegdno());
                String name         = student != null ? student.getName() : "";
                String branch       = student != null ? student.getBranchCode() : "";
                String decidedAt    = rs.getDecidedAt() != null
                    ? rs.getDecidedAt().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) : "";
                String remarks      = rs.getRemarks() != null ? rs.getRemarks().replace(",", ";") : "";
                pw.printf("%d,%s,\"%s\",%s,%s,%s,\"%s\",%s%n",
                    app.getId(), app.getRegdno(),
                    name, branch, rs.getRound(), rs.getStatus().name(),
                    remarks, decidedAt);
            }
        }

        log.info("CSV exported for drive={} round={} status={} rows={}",
            driveId, roundFilter, statusFilter, records.size());
        return baos.toByteArray();
    }

    // ── 6. Get Available Rounds ───────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<String> getAvailableRounds(Long driveId) {
        return driveJDRepository.findByDriveIdWithSteps(driveId)
            .map(jd -> jd.getSelectionSteps().stream()
                .filter(DriveJDSelectionStep::isEliminationRound)
                .map(step -> step.getDescription()
                    .trim().toUpperCase().replace(" ", "_"))
                .collect(Collectors.toList()))
            .orElse(List.of());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Drive findDriveOrThrow(Long driveId) {
        return driveRepository.findById(driveId)
            .orElseThrow(() -> new RuntimeException("Drive not found: " + driveId));
    }

    private Application findApplicationOrThrow(Long applicationId) {
        return applicationRepository.findById(applicationId)
            .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));
    }

    /**
     * Validates that the given roundName is one of the drive's configured elimination rounds.
     * Throws IllegalArgumentException if not found — results in 400 response.
     */
    private void validateRound(Long driveId, String roundName) {
        List<String> available = getAvailableRounds(driveId);
        if (!available.contains(roundName)) {
            String available_str = available.isEmpty() ? "(none configured)" : String.join(", ", available);
            throw new IllegalArgumentException(
                "Invalid round: '" + roundName + "'. Available rounds for drive " +
                driveId + ": " + available_str);
        }
    }

    /**
     * Creates an in-app notification record.
     * Returns true if notification was created, false if duplicate (already notified).
     */
    private boolean sendInAppNotification(Application application, String round, ShortlistStatus status) {
        // Idempotency: don't double-notify
        if (notificationRepository.existsByApplicationIdAndRoundAndStatus(
                application.getId(), round, status)) {
            log.debug("Notification already sent for app={} round={} status={}",
                application.getId(), round, status);
            return false;
        }

        ShortlistNotification notif = ShortlistNotification.builder()
            .application(application)
            .round(round)
            .statusNotified(status)
            .notificationType(NotificationType.IN_APP)
            .sentAt(LocalDateTime.now())
            .build();
        notificationRepository.save(notif);
        log.info("In-app notification sent: app={} round={} status={}",
            application.getId(), round, status);
        return true;
    }

    // ── 7. Get Student Notifications ──────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public com.trident.placement.dto.student.StudentNotificationResponse getStudentNotifications(String regdno, int page, int pageSize) {
        // Find all notifications for student, ordered by sentAt descending in the query
        List<ShortlistNotification> allNotifications = notificationRepository.findByApplication_Regdno(regdno);

        int totalElements = allNotifications.size();
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalElements);
        
        List<ShortlistNotification> pagedNotifications = fromIndex < totalElements 
            ? allNotifications.subList(fromIndex, toIndex) 
            : Collections.emptyList();

        List<com.trident.placement.dto.student.StudentNotificationDTO> dtos = pagedNotifications.stream().map(sn -> {
            Drive drive = sn.getApplication().getDrive();
            return com.trident.placement.dto.student.StudentNotificationDTO.builder()
                .id(sn.getId())
                .driveId(drive.getId())
                .driveName(drive.getCompanyName() + " - " + drive.getRole())
                .roundName(sn.getRound())
                .status(sn.getStatusNotified().name())
                .sentAt(sn.getSentAt() != null ? sn.getSentAt().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "")
                .build();
        }).collect(Collectors.toList());

        return com.trident.placement.dto.student.StudentNotificationResponse.builder()
            .data(dtos)
            .total(totalElements)
            .page(page)
            .pageSize(pageSize)
            .build();
    }
}
