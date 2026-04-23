package com.trident.placement.service;

import com.trident.placement.entity.Drive;
import com.trident.placement.entity.DriveEligibility;
import com.trident.placement.entity.EligibleDrive;
import com.trident.placement.entity.Student;
import com.trident.placement.entity.StudentCareer;
import com.trident.placement.entity.StudentCgpa;
import com.trident.placement.repository.EligibleDriveRepository;
import com.trident.placement.repository.AdminDriveRepository;
import com.trident.placement.repository.DriveEligibilityRepository;
import com.trident.placement.repository.StudentCareerRepository;
import com.trident.placement.repository.StudentCgpaRepository;
import com.trident.placement.repository.StudentRepository;
import com.trident.placement.util.BranchCodeUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core service for CGPA-based drive eligibility.
 *
 * TWO responsibilities:
 *
 * 1. CGPA Refresh (admin-triggered, runs once per semester):
 *    Admin clicks "Refresh CGPA" → fetches all students' CGPAs from BPUT
 *    → stores in student_cgpa table.
 *
 * 2. Eligibility Assignment (runs when drive is posted):
 *    Uses stored CGPA from student_cgpa table (no BPUT call)
 *    → compares with drive.minimumCgpa
 *    → inserts into eligible_drives table WITH branchCode populated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CgpaEligibilityService {

    private final StudentRepository studentRepository;
    private final StudentCgpaRepository studentCgpaRepository;
    private final StudentCareerRepository studentCareerRepository;
    private final EligibleDriveRepository eligibleDriveRepository;
    private final DriveEligibilityRepository driveEligibilityRepository;
    private final AdminDriveRepository adminDriveRepository;
    private final BputResultService bputResultService;
    private final SessionCalculator sessionCalculator;

    // ── DTO returned by getStoredCgpaInfo() ──────────────────────────────────

    @Data
    @Builder
    @AllArgsConstructor
    public static class StudentCgpaInfo {
        private String regdno;
        private String cgpa;           // "8.45" or "Not fetched yet"
        private String lastFetchedAt;  // "23-03-2026 14:30" or "Never"
        private boolean hasCgpa;
    }

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // ── 1. CGPA REFRESH (admin-triggered) ────────────────────────────────────

    /**
     * Fetches CGPA for ALL students from BPUT and stores in student_cgpa table.
     *
     * Runs asynchronously — admin API responds immediately.
     * This can take a long time (3-5 seconds per student × number of students).
     *
     * Always overwrites existing CGPA records with fresh data.
     * Call this once per semester after results are published on BPUT.
     */
    @Async("cgpaTaskExecutor")
    public void refreshAllStudentCgpa() {
        log.info("========== CGPA BULK REFRESH STARTED — 4th-year students (2021+ batch) only ==========");

        List<Student> allStudents = studentRepository.findFinalYearStudents();
        log.info("Total eligible students to refresh CGPA: {}", allStudents.size());

        int success = 0;
        int failed  = 0;
        int skipped = 0;

        for (Student student : allStudents) {
            if (student.getRegdno() == null || student.getRegdno().isBlank()) {
                skipped++;
                continue;
            }

            BigDecimal cgpa = fetchFromBput(student);

            if (cgpa != null) {
                storeCgpa(student.getRegdno(), cgpa);
                success++;
            } else {
                failed++;
            }
        }

        log.info("========== CGPA BULK REFRESH COMPLETE ==========");
        log.info("Success: {}, Failed: {}, Skipped: {}", success, failed, skipped);
    }

    /**
     * Refreshes CGPA for a single student.
     * Used when one student's result was updated after bulk refresh.
     */
    @Async("cgpaTaskExecutor")
    public void refreshSingleStudentCgpa(String regdno) {
        Student student = studentRepository.findById(regdno)
                .orElseThrow(() -> new RuntimeException("Student not found: " + regdno));

        BigDecimal cgpa = fetchFromBput(student);

        if (cgpa != null) {
            storeCgpa(regdno, cgpa);
            log.info("CGPA refreshed for {}: {}", regdno, cgpa);
        } else {
            log.warn("Could not fetch CGPA for {} from BPUT", regdno);
        }
    }

    // ── 2. ELIGIBILITY ASSIGNMENT (called when drive is posted) ──────────────

    /**
     * Schedules {@link #assignEligibleStudents(Drive)} after the current transaction
     * commits so DRIVES / DRIVE_ELIGIBILITY rows are visible to the async worker.
     */
    public void scheduleAssignEligibleStudentsAfterCommit(Long driveId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("No active transaction — scheduling eligibility immediately for drive {}", driveId);
            Drive drive = adminDriveRepository.findById(driveId).orElse(null);
            if (drive != null) {
                assignEligibleStudents(drive);
            }
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Drive drive = adminDriveRepository.findById(driveId).orElse(null);
                if (drive != null) {
                    assignEligibleStudents(drive);
                }
            }
        });
    }

    /**
     * Entry point — runs after the drive transaction commits (see AdminDriveServiceImpl).
     * Loads the drive fresh so DRIVE_ELIGIBILITY rows are visible — avoids async races.
     */
    @Async("cgpaTaskExecutor")
    public void assignEligibleStudents(Drive drive) {
        if (drive == null) {
            log.warn("assignEligibleStudents: drive is null — skipping");
            return;
        }
        assignEligibleStudentsInternal(drive);
    }

    /**
     * Core eligibility assignment logic.
     *
     * Iterates every DRIVE_ELIGIBILITY row (one per branch/course/year combo),
     * finds all matching students by CGPA + branch + course + passoutYear,
     * and inserts an ELIGIBLE_DRIVES row for each — WITH branchCode stored.
     *
     * FIX: EligibleDrive.builder() now includes .branchCode(branchCode).
     * Previously branchCode was never stored, so the dashboard query's
     * WHERE e.branchCode = :branchCode filter never matched anything,
     * causing ALL drives to show for ALL students regardless of branch.
     */
    private void assignEligibleStudentsInternal(Drive drive) {
        log.info("Assigning eligibility for drive: {} (id={}, minCgpa={}) — 4th-year students (2021+ batch) only",
                drive.getCompanyName(), drive.getId(), drive.getMinimumCgpa());

        // Load all eligibility rules (branch + course + year) for this drive
        List<DriveEligibility> eligibilityRows = driveEligibilityRepository.findByDriveId(drive.getId());

        if (eligibilityRows.isEmpty()) {
            log.warn("Drive id={} has no eligibility rows in DRIVE_ELIGIBILITY — no students will be marked eligible. "
                    + "Admin must select at least one branch when creating/updating the drive.",
                    drive.getId());
            return;
        }

        int totalEligible = 0;
        List<EligibleDrive> toInsert = new ArrayList<>();

        // Tracks regdno values already queued in this run — prevents duplicate
        // inserts when a student matches more than one eligibility row.
        Set<String> alreadyAdded = new HashSet<>();

        for (DriveEligibility eligRow : eligibilityRows) {

            // ── Resolve effective criteria for this row ───────────────────────
            String branchCode  = eligRow.getBranchCode().trim().toUpperCase();
            String course      = eligRow.getCourse()      != null ? eligRow.getCourse()      : drive.getEligibleCourse();
            Long   passoutYear = eligRow.getPassoutYear() != null ? eligRow.getPassoutYear() : drive.getPassoutYear();

            log.debug("Processing eligibility row: branch={}, course={}, passout_year={}",
                    branchCode, course, passoutYear);

            // ── Query students matching this row ──────────────────────────────
            Set<String> branchSet = new HashSet<>();
            branchSet.add(branchCode);

            List<Student> eligibleStudents;
            if (course == null || passoutYear == null) {
                log.debug("Course or PassoutYear is null — querying by branch + CGPA only for branch: {}", branchCode);
                eligibleStudents = studentRepository.findEligibleStudentsForDriveWithBranches(
                        drive.getMinimumCgpa(),
                        branchSet);
            } else {
                log.debug("Course and PassoutYear provided — querying by branch + course + year + CGPA");
                eligibleStudents = studentRepository.findEligibleStudentsWithCourseAndYop(
                        drive.getMinimumCgpa(),
                        course,
                        passoutYear,
                        branchSet);
            }

            log.debug("Eligibility row (branch={}, course={}, year={}) matched {} students",
                    branchCode, course, passoutYear, eligibleStudents.size());

            // ── Build EligibleDrive records ───────────────────────────────────
            for (Student student : eligibleStudents) {
                if (student.getRegdno() == null || student.getRegdno().isBlank()) {
                    continue;
                }

                String regdno = student.getRegdno();

                // Skip if already queued in this batch OR already in DB
                if (alreadyAdded.contains(regdno)
                        || eligibleDriveRepository.existsByRegdnoAndDriveId(regdno, drive.getId())) {
                    continue;
                }

                // ── FIX: .branchCode(branchCode) was missing in the original code ──
                // Without this, ELIGIBLE_DRIVES.BRANCH_CODE was always NULL, so the
                // dashboard query "WHERE e.branchCode = :branchCode" never matched,
                // and every student saw every drive regardless of their branch.
                toInsert.add(EligibleDrive.builder()
                        .regdno(regdno)
                        .drive(drive)
                        .branchCode(branchCode)   // ← THE CRITICAL FIX
                        .build());

                alreadyAdded.add(regdno);
                totalEligible++;
            }
        }

        if (!toInsert.isEmpty()) {
            saveEligibleDrivesBatch(toInsert);
        }

        log.info("Drive {} eligibility done — eligible 4th-year students inserted: {}",
                drive.getId(), totalEligible);
    }

    // ── 3. STUDENT DASHBOARD — fetch eligible drives ──────────────────────────

    /**
     * Returns all OPEN drives a student is eligible for.
     *
     * Filters by:
     *   1. Branch       — student's branchCode must match drive's DRIVE_ELIGIBILITY rows
     *   2. Career marks — student's 10th / 12th / diploma / graduation % must meet
     *                     the drive's minimum thresholds (null threshold = no requirement)
     *
     * Career marks are read from STUDENT_CAREER table.
     * If a student has no career record, marks are treated as null —
     * they will only see drives that have no career marks requirements set.
     *
     * Zero values (0.0) in STUDENT_CAREER are treated as "not applicable"
     * (e.g. diploma=0 means the student did not pursue a diploma — not filtered on).
     */
    @Transactional(readOnly = true)
public List<Drive> getEligibleDrivesForStudent(String regdno) {
    Student student = studentRepository.findById(regdno)
            .orElseThrow(() -> new RuntimeException("Student not found: " + regdno));

    String branchCode = student.getBranchCode() != null
            ? student.getBranchCode().trim().toUpperCase()
            : "";

    // Fetch career marks
    StudentCareer career = studentCareerRepository.findByRegdno(regdno).orElse(null);

    BigDecimal tenth      = resolveCareerMark(career != null ? career.getTenthPercentage()      : null);
    BigDecimal twelfth    = resolveCareerMark(career != null ? career.getTwelvthPercentage()     : null);
    BigDecimal diploma    = resolveCareerMark(career != null ? career.getDiplomaPercentage()     : null);
    BigDecimal graduation = resolveCareerMark(career != null ? career.getGraduationPercentage()  : null);

    // ── CRITICAL DEBUG LOG — check what values are actually being passed ──
    log.info("ELIGIBILITY CHECK | student={} | branch={} | 10th={} | 12th={} | diploma={} | graduation={}",
            regdno, branchCode, tenth, twelfth, diploma, graduation);

    return adminDriveRepository.findOpenDrivesByBranchAndCareerMarks(
            branchCode, tenth, twelfth, diploma, graduation);
}

private BigDecimal resolveCareerMark(BigDecimal value) {
    return (value == null || value.compareTo(BigDecimal.ZERO) <= 0) ? null : value;
}

    // ── 4. ADMIN VIEW ─────────────────────────────────────────────────────────

    /**
     * Returns stored CGPA info for a student — used by admin UI.
     */
    @Transactional(readOnly = true)
    public StudentCgpaInfo getStoredCgpaInfo(String regdno) {
        var record = studentCgpaRepository.findByRegdno(regdno);

        if (record.isEmpty()) {
            return StudentCgpaInfo.builder()
                    .regdno(regdno)
                    .cgpa("Not fetched yet")
                    .lastFetchedAt("Never")
                    .hasCgpa(false)
                    .build();
        }

        StudentCgpa sc = record.get();
        return StudentCgpaInfo.builder()
                .regdno(regdno)
                .cgpa(sc.getCgpa().toPlainString())
                .lastFetchedAt(sc.getUpdatedAt().format(DT_FMT))
                .hasCgpa(true)
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Calls BputResultService to fetch and calculate CGPA for a student.
     * Returns null if BPUT has no data or the call fails.
     */
    private BigDecimal fetchFromBput(Student student) {
        String dob          = sessionCalculator.normalizeDob(student.getDob());
        String startSession = sessionCalculator.calculateStartSession(student.getAdmissionYear());
        String endSession   = sessionCalculator.calculateEndSession(student.getAdmissionYear());

        if (dob == null) {
            log.warn("No DOB for student {} — cannot fetch BPUT results", student.getRegdno());
            return null;
        }

        return bputResultService.fetchAndCalculateCgpa(
                student.getRegdno(), dob, startSession, endSession);
    }

    /**
     * Upsert CGPA — creates new record or updates existing one.
     */
    @Transactional
    public void storeCgpa(String regdno, BigDecimal cgpa) {
        studentCgpaRepository.findByRegdno(regdno).ifPresentOrElse(
                existing -> {
                    existing.setCgpa(cgpa);
                    studentCgpaRepository.save(existing);
                },
                () -> studentCgpaRepository.save(
                        StudentCgpa.builder()
                                .regdno(regdno)
                                .cgpa(cgpa)
                                .build()));
    }

    @Transactional
    public void saveEligibleDrivesBatch(List<EligibleDrive> records) {
        eligibleDriveRepository.saveAll(records);
    }
}