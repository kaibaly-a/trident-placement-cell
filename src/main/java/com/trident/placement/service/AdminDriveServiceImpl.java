package com.trident.placement.service;

import com.trident.placement.dto.DriveJDResponse;
import com.trident.placement.dto.admin.AdminDriveResponse;
import com.trident.placement.dto.admin.DriveCreateRequest;
import com.trident.placement.dto.admin.DriveUpdateRequest;
import com.trident.placement.dto.admin.EligibleStudentPreviewDTO;
import com.trident.placement.dto.admin.PublishDriveRequest;
import com.trident.placement.entity.Drive;
import com.trident.placement.entity.DriveEligibility;
import com.trident.placement.entity.DriveJD;
import com.trident.placement.entity.DriveJDSelectionStep;
import com.trident.placement.entity.Student;
import com.trident.placement.entity.StudentCareer;
import com.trident.placement.enums.DriveStatus;
import com.trident.placement.enums.DriveType;
import com.trident.placement.repository.AdminApplicationRepository;
import com.trident.placement.repository.AdminDriveRepository;
import com.trident.placement.repository.DriveEligibilityRepository;
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

    private final AdminDriveRepository adminDriveRepository;
    private final AdminApplicationRepository adminApplicationRepository;
    private final EligibleDriveRepository eligibleDriveRepository;
    private final DriveJDRepository driveJDRepository;
    private final DriveEligibilityRepository driveEligibilityRepository;
    private final com.trident.placement.repository.StudentRepository studentRepository;
    private final com.trident.placement.repository.StudentCareerRepository studentCareerRepository;

    // Injected to trigger eligibility AFTER drive creation
    private final CgpaEligibilityService cgpaEligibilityService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AdminDriveResponse> getAllDrivesWithCounts() {
        List<Drive> drives = adminDriveRepository.findAll();
        List<Object[]> countRows = adminDriveRepository.findAllDrivesWithCounts();

        Map<Long, long[]> countsMap = new HashMap<>();
        for (Object[] row : countRows) {
            Long driveId = ((Number) row[0]).longValue();
            long total = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long shortlisted = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            long approved = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            countsMap.put(driveId, new long[] { total, shortlisted, approved });
        }

        return drives.stream()
                .map(drive -> {
                    long[] counts = countsMap.getOrDefault(
                            drive.getId(), new long[] { 0, 0, 0 });
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
                BranchCodeUtils.normalizeList(request.getEligibleBranches()), List.of());
        if (branchList.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one valid branch code is required (e.g. CSE, ETC).");
        }

        // ── 1. Save the core Drive (without branches — stored in DRIVE_ELIGIBILITY) ──
        Drive drive = Drive.builder()
                .companyName(request.getCompanyName().trim())
                .role(request.getRole().trim())
                .driveType(driveType)
                .lpaPackage(request.getLpaPackage())
                .minimumCgpa(request.getMinimumCgpa())
                .lastDate(request.getLastDate())
                .description(request.getDescription() != null
                        ? request.getDescription().trim()
                        : null)
                .status(DriveStatus.DRAFT)          // ← DRAFT: not visible to students yet
                .eligibleCourse(request.getEligibleCourse())
                .passoutYear(request.getPassoutYear())
                // Career marks criteria — null means no minimum required
                .minTenthPercent(request.getMinTenthPercent())
                .minTwelfthPercent(request.getMinTwelfthPercent())
                .minDiplomaPercent(request.getMinDiplomaPercent())
                .minGraduationPercent(request.getMinGraduationPercent())
                .build();

        Drive saved = adminDriveRepository.save(drive);
        log.info("Admin created drive: {} (id={}) branches={}", saved.getCompanyName(), saved.getId(), branchList);

        // ── 2. Save one DRIVE_ELIGIBILITY row per branch ──────────────────────
        String course = request.getEligibleCourse();
        Long passoutYear = request.getPassoutYear();
        List<DriveEligibility> eligRows = branchList.stream()
                .map(branch -> DriveEligibility.builder()
                        .drive(saved)
                        .branchCode(branch)
                        .course(course)
                        .passoutYear(passoutYear)
                        .build())
                .collect(Collectors.toList());
        driveEligibilityRepository.saveAll(eligRows);
        log.info("Saved {} DRIVE_ELIGIBILITY rows for drive id={}", eligRows.size(), saved.getId());

        // ── 3. Save the Job Description (if any JD fields are present) ────────
        buildAndSaveJd(saved, request);

        // ── 4. Async eligibility assignment (branch + CGPA filtered) ─────────
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

        // ── Update drive fields FIRST ──────────────────────────────────────
        if (request.getCompanyName() != null)
            drive.setCompanyName(request.getCompanyName().trim());
        if (request.getRole() != null)
            drive.setRole(request.getRole().trim());
        if (request.getDriveType() != null)
            drive.setDriveType(parseDriveType(request.getDriveType()));
        if (request.getLpaPackage() != null)
            drive.setLpaPackage(request.getLpaPackage());
        if (request.getMinimumCgpa() != null)
            drive.setMinimumCgpa(request.getMinimumCgpa());
        if (request.getLastDate() != null)
            drive.setLastDate(request.getLastDate());
        if (request.getDescription() != null)
            drive.setDescription(request.getDescription().trim());
        if (request.getEligibleCourse() != null)
            drive.setEligibleCourse(request.getEligibleCourse());
        if (request.getPassoutYear() != null)
            drive.setPassoutYear(request.getPassoutYear());
        // Career marks — only update if explicitly provided
        if (request.getMinTenthPercent() != null)
            drive.setMinTenthPercent(request.getMinTenthPercent());
        if (request.getMinTwelfthPercent() != null)
            drive.setMinTwelfthPercent(request.getMinTwelfthPercent());
        if (request.getMinDiplomaPercent() != null)
            drive.setMinDiplomaPercent(request.getMinDiplomaPercent());
        if (request.getMinGraduationPercent() != null)
            drive.setMinGraduationPercent(request.getMinGraduationPercent());

        // ── Then handle branches (which uses the updated course/passoutYear) ──
        boolean branchesChanged = false;
        if (request.getBranches() != null) {
            List<String> normalized = BranchCodeUtils.normalizeList(request.getBranches());
            // Compare existing branch codes with requested ones
            List<String> existingBranches = driveEligibilityRepository.findByDriveId(id)
                    .stream().map(DriveEligibility::getBranchCode).collect(Collectors.toList());
            branchesChanged = !BranchCodeUtils.sameBranchSet(existingBranches, normalized);

            if (branchesChanged && !normalized.isEmpty()) {
                // Replace all DRIVE_ELIGIBILITY rows for this drive
                driveEligibilityRepository.deleteByDriveId(id);
                List<DriveEligibility> newRows = normalized.stream()
                        .map(branch -> DriveEligibility.builder()
                                .drive(drive)
                                .branchCode(branch)
                                .course(drive.getEligibleCourse())
                                .passoutYear(drive.getPassoutYear())
                                .build())
                        .collect(Collectors.toList());
                driveEligibilityRepository.saveAll(newRows);
                log.info("Updated DRIVE_ELIGIBILITY rows for drive id={}: {}", id, normalized);
            }
        }

        Drive updated = adminDriveRepository.save(drive);
        log.info("Admin updated drive: {} (id={})", updated.getCompanyName(), updated.getId());

        // If eligible course or passout year changed, update all DRIVE_ELIGIBILITY rows
        if ((request.getEligibleCourse() != null || request.getPassoutYear() != null) && !branchesChanged) {
            List<DriveEligibility> existingRows = driveEligibilityRepository.findByDriveId(id);
            existingRows.forEach(row -> {
                if (request.getEligibleCourse() != null)
                    row.setCourse(request.getEligibleCourse());
                if (request.getPassoutYear() != null)
                    row.setPassoutYear(request.getPassoutYear());
            });
            driveEligibilityRepository.saveAll(existingRows);
            log.info("Updated DRIVE_ELIGIBILITY course/passoutYear for drive id={}", id);
        }

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
                ? DriveStatus.CLOSED
                : DriveStatus.OPEN;

        drive.setStatus(newStatus);
        Drive updated = adminDriveRepository.save(drive);
        log.info("Admin toggled drive id={} status to {}", id, newStatus);

        long[] counts = getCounts(id);
        return toDTO(updated, counts[0], counts[1], counts[2]);
    }

    // ── Review & Publish flow ─────────────────────────────────────────────────

    /**
     * GET /api/admin/drives/{id}/eligible-students
     *
     * Returns all students who are eligible for this drive based on:
     *   1. Branch match (DRIVE_ELIGIBILITY rows)
     *   2. Career marks criteria (min 10th / 12th / diploma / graduation %)
     *
     * Called after admin hits "Next" on the create form.
     * Admin reviews the list, selects/deselects students, then hits "Publish".
     */
    @Override
    @Transactional(readOnly = true)
    public List<EligibleStudentPreviewDTO> getEligibleStudentPreviews(Long driveId) {
        Drive drive = findDriveOrThrow(driveId);

        // Get all branches for this drive
        List<DriveEligibility> eligibilityRows = driveEligibilityRepository.findByDriveId(driveId);
        if (eligibilityRows.isEmpty()) {
            log.warn("Drive id={} has no eligibility rows — returning empty student list", driveId);
            return List.of();
        }

        // Collect all students matching branch criteria
        java.util.Set<String> processedRegdnos = new java.util.HashSet<>();
        List<EligibleStudentPreviewDTO> result = new ArrayList<>();

        for (DriveEligibility row : eligibilityRows) {
            String branchCode  = row.getBranchCode().trim().toUpperCase();
            String course      = row.getCourse()      != null ? row.getCourse()      : drive.getEligibleCourse();
            Long   passoutYear = row.getPassoutYear() != null ? row.getPassoutYear() : drive.getPassoutYear();

            java.util.Set<String> branchSet = java.util.Collections.singleton(branchCode);

            List<Student> students;
            if (course == null || passoutYear == null) {
                // Fallback: branch only, no CGPA required
                students = studentRepository.findStudentsForPreviewByBranch(branchSet);
            } else {
                // Branch + course + year, no CGPA required
                students = studentRepository.findStudentsForPreviewByCourseAndYop(
                        course, passoutYear, branchSet);
            }

            for (Student student : students) {
                if (student.getRegdno() == null || processedRegdnos.contains(student.getRegdno())) {
                    continue;
                }

                // Apply career marks filter
                StudentCareer career = studentCareerRepository
                        .findByRegdno(student.getRegdno()).orElse(null);

                if (!passesCareerFilter(career, drive)) {
                    continue;
                }

                processedRegdnos.add(student.getRegdno());

                result.add(EligibleStudentPreviewDTO.builder()
                        .regdno(student.getRegdno())
                        .name(student.getName())
                        .branchCode(student.getBranchCode())
                        .course(student.getCourse())
                        .degreeYop(student.getDegreeYop())
                        .tenthPercentage(career != null && career.getTenthPercentage()     != null
                                ? career.getTenthPercentage().doubleValue()     : null)
                        .twelfthPercentage(career != null && career.getTwelvthPercentage()  != null
                                ? career.getTwelvthPercentage().doubleValue()   : null)
                        .diplomaPercentage(career != null && career.getDiplomaPercentage()  != null
                                ? career.getDiplomaPercentage().doubleValue()   : null)
                        .graduationPercentage(career != null && career.getGraduationPercentage() != null
                                ? career.getGraduationPercentage().doubleValue() : null)
                        .build());
            }
        }

        log.info("Drive {} eligible student preview: {} students", driveId, result.size());
        return result;
    }

    /**
     * PATCH /api/admin/drives/{id}/publish
     *
     * Transitions a DRAFT drive to OPEN status.
     * Only the regdnos in request.selectedRegdnos will see this drive
     * (stored in ELIGIBLE_DRIVES with their branchCode).
     *
     * If selectedRegdnos is null/empty, falls back to ALL eligible students.
     */
    @Override
    @Transactional
    public AdminDriveResponse publishDrive(Long driveId, PublishDriveRequest request) {
        Drive drive = findDriveOrThrow(driveId);

        if (drive.getStatus() != com.trident.placement.enums.DriveStatus.DRAFT) {
            throw new IllegalStateException(
                    "Drive '" + drive.getCompanyName() + "' is not in DRAFT status. " +
                    "Only DRAFT drives can be published.");
        }

        List<String> selectedRegdnos = request.getSelectedRegdnos();

        // If admin didn't explicitly select anyone, include ALL eligible students
        if (selectedRegdnos == null || selectedRegdnos.isEmpty()) {
            log.info("No students explicitly selected for drive {} — including all eligible students", driveId);
            selectedRegdnos = getEligibleStudentPreviews(driveId)
                    .stream()
                    .map(EligibleStudentPreviewDTO::getRegdno)
                    .collect(Collectors.toList());
        }

        // Clear any existing eligible_drives rows (safety — shouldn't exist for DRAFT)
        eligibleDriveRepository.deleteByDriveId(driveId);

        // Insert ELIGIBLE_DRIVES rows only for selected students
        // Branch code is resolved by looking up each student
        List<com.trident.placement.entity.EligibleDrive> toInsert = new ArrayList<>();
        for (String regdno : selectedRegdnos) {
            studentRepository.findById(regdno).ifPresent(student -> {
                String branchCode = student.getBranchCode() != null
                        ? student.getBranchCode().trim().toUpperCase() : "";
                toInsert.add(com.trident.placement.entity.EligibleDrive.builder()
                        .regdno(regdno)
                        .drive(drive)
                        .branchCode(branchCode)
                        .build());
            });
        }

        if (!toInsert.isEmpty()) {
            eligibleDriveRepository.saveAll(toInsert);
        }

        // Flip status to OPEN — now visible to selected students
        drive.setStatus(com.trident.placement.enums.DriveStatus.OPEN);
        Drive published = adminDriveRepository.save(drive);

        log.info("Drive {} '{}' published — {} students notified",
                driveId, published.getCompanyName(), toInsert.size());

        long[] counts = getCounts(driveId);
        return toDTO(published, counts[0], counts[1], counts[2]);
    }

    /**
     * Returns true if the student's career marks meet the drive's minimum criteria.
     * A null drive criterion means no requirement for that field.
     * A null or zero student mark means the field is not applicable for them.
     */
    private boolean passesCareerFilter(StudentCareer career, Drive drive) {
        return passesOneMark(career != null ? career.getTenthPercentage()      : null, drive.getMinTenthPercent()) &&
               passesOneMark(career != null ? career.getTwelvthPercentage()    : null, drive.getMinTwelfthPercent()) &&
               passesOneMark(career != null ? career.getDiplomaPercentage()    : null, drive.getMinDiplomaPercent()) &&
               passesOneMark(career != null ? career.getGraduationPercentage() : null, drive.getMinGraduationPercent());
    }

    private boolean passesOneMark(java.math.BigDecimal studentMark, java.math.BigDecimal driveMin) {
        if (driveMin == null) return true;                          // No requirement set
        if (studentMark == null || studentMark.compareTo(java.math.BigDecimal.ZERO) <= 0) return false; // No mark = fails
        return studentMark.compareTo(driveMin) >= 0;               // Must meet or exceed minimum
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
                // cgpaCutoffDisplay: prefer explicit display string, fall back to
                // minimumCgpa.toString()
                .cgpaCutoff(req.getCgpaCutoffDisplay() != null
                        ? req.getCgpaCutoffDisplay()
                        : req.getMinimumCgpa().toPlainString())
                .backlogsAllowed(Boolean.TRUE.equals(req.getBacklogsAllowed()) ? true : false)
                .allowedBranches(listToPipe(req.getEligibleBranches()))
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
        if (list == null || list.isEmpty())
            return null;
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
        // Derive branch list from DRIVE_ELIGIBILITY rows
        List<String> branches = drive.getEligibilityRows() == null ? List.of()
                : drive.getEligibilityRows().stream()
                        .map(DriveEligibility::getBranchCode)
                        .distinct()
                        .collect(Collectors.toList());

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
                .branches(branches)
                .eligibleCourse(drive.getEligibleCourse())
                .passoutYear(drive.getPassoutYear())
                .minTenthPercent(drive.getMinTenthPercent())
                .minTwelfthPercent(drive.getMinTwelfthPercent())
                .minDiplomaPercent(drive.getMinDiplomaPercent())
                .minGraduationPercent(drive.getMinGraduationPercent())
                .build();
    }

    private long[] getCounts(Long driveId) {
        List<Object[]> results = adminDriveRepository.findApplicationCountsByDriveId(driveId);
        if (results == null || results.isEmpty() || results.get(0) == null) {
            return new long[] { 0L, 0L, 0L };
        }
        Object[] raw = results.get(0);
        long total = raw[0] != null ? ((Number) raw[0]).longValue() : 0L;
        long shortlisted = raw[1] != null ? ((Number) raw[1]).longValue() : 0L;
        long approved = raw[2] != null ? ((Number) raw[2]).longValue() : 0L;
        return new long[] { total, shortlisted, approved };
    }
}