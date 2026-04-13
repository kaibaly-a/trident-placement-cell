package com.trident.placement.service;

import com.trident.placement.dto.DriveJDResponse;
import com.trident.placement.dto.admin.AdminDriveResponse;
import com.trident.placement.dto.admin.DriveCreateRequest;
import com.trident.placement.dto.admin.DriveUpdateRequest;
import com.trident.placement.entity.Drive;
import com.trident.placement.entity.DriveJD;
import com.trident.placement.entity.DriveJDSelectionStep;
import com.trident.placement.enums.DriveStatus;
import com.trident.placement.enums.DriveType;
import com.trident.placement.repository.AdminApplicationRepository;
import com.trident.placement.repository.AdminDriveRepository;
import com.trident.placement.repository.DriveJDRepository;
import com.trident.placement.repository.EligibleDriveRepository;
import com.trident.placement.util.BranchCodeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDriveServiceImpl implements AdminDriveService {

    private final AdminDriveRepository       adminDriveRepository;
    private final AdminApplicationRepository adminApplicationRepository;
    private final EligibleDriveRepository    eligibleDriveRepository;
    private final DriveJDRepository          driveJDRepository;

    // Injected to trigger eligibility AFTER drive creation
    private final CgpaEligibilityService     cgpaEligibilityService;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AdminDriveResponse> getAllDrivesWithCounts() {
        List<Drive> drives = adminDriveRepository.findAll();
        List<Object[]> countRows = adminDriveRepository.findAllDrivesWithCounts();

        Map<Long, long[]> countsMap = new HashMap<>();
        for (Object[] row : countRows) {
            Long driveId     = ((Number) row[0]).longValue();
            long total       = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long shortlisted = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            long approved    = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            countsMap.put(driveId, new long[]{total, shortlisted, approved});
        }

        return drives.stream()
                .map(drive -> {
                    long[] counts = countsMap.getOrDefault(
                            drive.getId(), new long[]{0, 0, 0});
                    return toDTO(drive, counts[0], counts[1], counts[2]);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDriveResponse getDriveById(Long id) {
        Drive drive = findDriveOrThrow(id);
        long[] counts = getCounts(id);
        return toDTO(drive, counts[0], counts[1], counts[2]);
    }

    // ── Create (unified: Drive + JD in one transaction) ───────────────────────

    @Override
    @Transactional
    public AdminDriveResponse createDrive(DriveCreateRequest request) {
        DriveType driveType = parseDriveType(request.getDriveType());

        // Normalize and validate branch list
        List<String> branchList = Objects.requireNonNullElse(
                BranchCodeUtils.normalizeList(request.getAllowedBranches()), List.of());
        if (branchList.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one valid branch code is required (e.g. CSE, ETC).");
        }

        // ── 1. Save the core Drive ────────────────────────────────────────────
        Drive drive = Drive.builder()
                .companyName(request.getCompanyName().trim())
                .role(request.getRole().trim())
                .driveType(driveType)
                .lpaPackage(request.getLpaPackage())
                .minimumCgpa(request.getMinimumCgpa())
                .lastDate(request.getLastDate())
                .description(request.getDescription() != null
                        ? request.getDescription().trim() : null)
                .branches(branchList)
                .status(DriveStatus.OPEN)
                .build();

        Drive saved = adminDriveRepository.save(drive);
        log.info("Admin created drive: {} (id={}) branches={}", saved.getCompanyName(), saved.getId(), branchList);

        // ── 2. Save the Job Description (if any JD fields are present) ────────
        buildAndSaveJd(saved, request);

        // ── 3. Async eligibility assignment (branch + CGPA filtered) ─────────
        cgpaEligibilityService.scheduleAssignEligibleStudentsAfterCommit(saved.getId());

        return toDTO(saved, 0L, 0L, 0L);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AdminDriveResponse updateDrive(Long id, DriveUpdateRequest request) {
        Drive drive = findDriveOrThrow(id);

        boolean cgpaChanged = request.getMinimumCgpa() != null
                && !request.getMinimumCgpa().equals(drive.getMinimumCgpa());

        boolean branchesChanged = false;
        if (request.getBranches() != null) {
            List<String> normalized = BranchCodeUtils.normalizeList(request.getBranches());
            branchesChanged = !BranchCodeUtils.sameBranchSet(drive.getBranches(), normalized);
            drive.setBranches(normalized);
        }

        if (request.getCompanyName() != null) drive.setCompanyName(request.getCompanyName().trim());
        if (request.getRole() != null)        drive.setRole(request.getRole().trim());
        if (request.getDriveType() != null)   drive.setDriveType(parseDriveType(request.getDriveType()));
        if (request.getLpaPackage() != null)  drive.setLpaPackage(request.getLpaPackage());
        if (request.getMinimumCgpa() != null) drive.setMinimumCgpa(request.getMinimumCgpa());
        if (request.getLastDate() != null)    drive.setLastDate(request.getLastDate());
        if (request.getDescription() != null) drive.setDescription(request.getDescription().trim());

        Drive updated = adminDriveRepository.save(drive);
        log.info("Admin updated drive: {} (id={})", updated.getCompanyName(), updated.getId());

        // If minimumCgpa or branches changed → re-run eligibility for this drive
        if (cgpaChanged || branchesChanged) {
            log.info("Eligibility criteria changed for drive {} — re-running eligibility", id);
            eligibleDriveRepository.deleteByDriveId(id);
            cgpaEligibilityService.scheduleAssignEligibleStudentsAfterCommit(id);
        }

        long[] counts = getCounts(id);
        return toDTO(updated, counts[0], counts[1], counts[2]);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteDrive(Long id) {
        Drive drive = findDriveOrThrow(id);

        long totalApplicants = getCounts(id)[0];

        if (totalApplicants > 0) {
            throw new IllegalStateException(
                    "Cannot delete drive '" + drive.getCompanyName() +
                    "' — it has " + totalApplicants + " application(s). " +
                    "Close the drive instead.");
        }

        // Clean up eligibility records first
        eligibleDriveRepository.deleteByDriveId(id);
        adminDriveRepository.deleteById(id);
        log.info("Admin deleted drive id={}", id);
    }

    // ── Toggle Status ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AdminDriveResponse toggleDriveStatus(Long id) {
        Drive drive = findDriveOrThrow(id);

        DriveStatus newStatus = drive.getStatus() == DriveStatus.OPEN
                ? DriveStatus.CLOSED : DriveStatus.OPEN;

        drive.setStatus(newStatus);
        Drive updated = adminDriveRepository.save(drive);
        log.info("Admin toggled drive id={} status to {}", id, newStatus);

        long[] counts = getCounts(id);
        return toDTO(updated, counts[0], counts[1], counts[2]);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Builds and saves a DriveJD from the unified create request.
     * If none of the JD-specific fields are present, no JD row is created
     * (admin can add it later via PATCH /api/admin/drives/{id}/jd).
     */
    private void buildAndSaveJd(Drive drive, DriveCreateRequest req) {
        // Only persist JD if at least one JD field is provided
        boolean hasJdContent = req.getAboutCompany() != null
                || req.getRoleOverview() != null
                || req.getJobLocation() != null
                || req.getSelectionProcess() != null;

        if (!hasJdContent) {
            log.debug("No JD fields in create request for drive id={} — skipping JD creation", drive.getId());
            return;
        }

        DriveJD jd = DriveJD.builder()
                .drive(drive)
                .jobLocation(req.getJobLocation())
                .employmentType(req.getEmploymentType())
                .workMode(req.getWorkMode())
                .vacancies(req.getVacancies())
                .serviceAgreement(req.getServiceAgreement())
                .joining(req.getJoining())
                // cgpaCutoffDisplay: prefer explicit display string, fall back to minimumCgpa.toString()
                .cgpaCutoff(req.getCgpaCutoffDisplay() != null
                        ? req.getCgpaCutoffDisplay()
                        : req.getMinimumCgpa().toPlainString())
                .backlogsAllowed(Boolean.TRUE.equals(req.getBacklogsAllowed()) ? true : false)
                .allowedBranches(listToPipe(req.getAllowedBranches()))
                .allowedCourses(listToPipe(req.getAllowedCourses()))
                .batch(req.getBatch())
                .aboutCompany(req.getAboutCompany())
                .website(req.getWebsite())
                .headquarters(req.getHeadquarters())
                .roleOverview(req.getRoleOverview())
                .requiredSkills(listToPipe(req.getRequiredSkills()))
                .keyResponsibilities(listToPipe(req.getKeyResponsibilities()))
                .whyJoin(listToPipe(req.getWhyJoin()))
                .build();

        // Attach selection steps
        if (req.getSelectionProcess() != null) {
            List<DriveJDSelectionStep> steps = req.getSelectionProcess().stream()
                    .filter(s -> s.getDescription() != null && !s.getDescription().isBlank())
                    .map(s -> DriveJDSelectionStep.builder()
                            .driveJD(jd)
                            .description(s.getDescription().trim())
                            .eliminationRound(s.isEliminationRound())
                            .build())
                    .collect(Collectors.toList());
            jd.getSelectionSteps().addAll(steps);
        }

        driveJDRepository.save(jd);
        log.info("JD created inline for drive id={} ({})", drive.getId(), drive.getCompanyName());
    }

    private String listToPipe(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.joining("|"));
    }

    private Drive findDriveOrThrow(Long id) {
        return adminDriveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Drive not found with id: " + id));
    }

    private DriveType parseDriveType(String type) {
        try {
            return DriveType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid drive type: '" + type + "'. Valid: ON_CAMPUS, OFF_CAMPUS, POOL");
        }
    }

    private AdminDriveResponse toDTO(Drive drive, long totalApplicants,
                                     long shortlistedCount, long selectedCount) {
        return AdminDriveResponse.builder()
                .id(drive.getId())
                .companyName(drive.getCompanyName())
                .role(drive.getRole())
                .driveType(drive.getDriveType().name())
                .lpaPackage(drive.getLpaPackage())
                .minimumCgpa(drive.getMinimumCgpa())
                .lastDate(drive.getLastDate().format(DATE_FMT))
                .description(drive.getDescription())
                .status(drive.getStatus().name())
                .totalApplicants(totalApplicants)
                .shortlistedCount(shortlistedCount)
                .selectedCount(selectedCount)
                .createdAt(drive.getCreatedAt())
                .updatedAt(drive.getUpdatedAt())
                .branches(drive.getBranches())
                .build();
    }

    private long[] getCounts(Long driveId) {
        List<Object[]> results = adminDriveRepository.findApplicationCountsByDriveId(driveId);
        if (results == null || results.isEmpty() || results.get(0) == null) {
            return new long[]{0L, 0L, 0L};
        }
        Object[] raw = results.get(0);
        long total       = raw[0] != null ? ((Number) raw[0]).longValue() : 0L;
        long shortlisted = raw[1] != null ? ((Number) raw[1]).longValue() : 0L;
        long approved    = raw[2] != null ? ((Number) raw[2]).longValue() : 0L;
        return new long[]{total, shortlisted, approved};
    }
}