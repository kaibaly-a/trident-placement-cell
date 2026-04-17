package com.trident.placement.service;

import com.trident.placement.entity.Drive;
import com.trident.placement.entity.EligibleDrive;
import com.trident.placement.entity.Student;
import com.trident.placement.entity.StudentCgpa;
import com.trident.placement.repository.EligibleDriveRepository;
import com.trident.placement.repository.AdminDriveRepository;
import com.trident.placement.repository.DriveJDRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core service for CGPA-based drive eligibility.
 *
 * TWO responsibilities:
 *
 * 1. CGPA Refresh (admin-triggered, runs once per semester):
 * Admin clicks "Refresh CGPA" → fetchs all students' CGPAs from BPUT
 * → stores in student_cgpa table.
 *
 * 2. Eligibility Assignment (runs when drive is posted):
 * Uses stored CGPA from student_cgpa table (no BPUT call)
 * → compares with drive.minimumCgpa
 * → inserts into eligible_drives table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CgpaEligibilityService {

    private final StudentRepository studentRepository;
    private final StudentCgpaRepository studentCgpaRepository;
    private final EligibleDriveRepository eligibleDriveRepository;
    private final AdminDriveRepository adminDriveRepository;
    private final DriveJDRepository driveJDRepository;
    private final BputResultService bputResultService;
    private final SessionCalculator sessionCalculator;

    // ── DTO returned by getStoredCgpaInfo() ──────────────────────────────────

    @Data
    @Builder
    @AllArgsConstructor
    public static class StudentCgpaInfo {
        private String regdno;
        private String cgpa; // "8.45" or "Not fetched yet"
        private String lastFetchedAt; // "23-03-2026 14:30" or "Never"
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

        // Only 4th-year students admitted in 2021 or later.
        // This excludes all alumni and pre-2021 batches entirely.
        List<Student> allStudents = studentRepository.findFinalYearStudents();
        log.info("Total eligible students to refresh CGPA: {}", allStudents.size());

        int success = 0;
        int failed = 0;
        int skipped = 0;

        for (Student student : allStudents) {
            if (student.getRegdno() == null || student.getRegdno().isBlank()) {
                skipped++;
                continue;
            }

            BigDecimal cgpa = fetchFromBput(student);

            if (cgpa != null) {
                storeCgpa(student.getRegdno(), cgpa); // upsert
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
                .orElseThrow(() -> new RuntimeException(
                        "Student not found: " + regdno));

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
     * Schedules {@link #assignEligibleStudents(Drive)} after the current transaction commits
     * so {@code DRIVES} / {@code DRIVE_BRANCHES} rows are visible to the async worker.
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
     * Runs after the drive transaction commits (see AdminDriveServiceImpl).
     * Loads the drive fresh so {@code DRIVE_BRANCHES} is visible — avoids async races.
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
     * Called by AdminDriveServiceImpl AFTER a drive is saved (via {@link #assignEligibleStudents(Drive)}).
     *
     * Uses STORED CGPA from student_cgpa table — NO BPUT call here.
     * If a student has no stored CGPA, they are skipped.
     *
     * Branch rule: if the drive has no branch rows, nobody is eligible (strict).
     * Otherwise only students whose BRANCH_CODE matches one of the drive's branches.
     *
     * Admin should run refreshAllStudentCgpa() before posting drives
     * to ensure CGPAs are up to date.
     */
    private void assignEligibleStudentsInternal(Drive drive) {
        log.info("Assigning eligibility for drive: {} (id={}, minCgpa={}) — 4th-year students (2021+ batch) only",
                drive.getCompanyName(), drive.getId(), drive.getMinimumCgpa());

        List<String> allowedBranches = getAllowedBranches(drive.getId());
        if (allowedBranches.isEmpty()) {
            log.warn("Drive id={} has no branch codes in DRIVE_BRANCHES — no students will be marked eligible. " +
                    "Admin must select at least one branch when creating/updating the drive.",
                    drive.getId());
            return;
        }
        log.info("Drive id={} restricted to branches: {}", drive.getId(), allowedBranches);

        // Convert to Set for compatibility with existing query
        Set<String> branchSet = new java.util.HashSet<>(allowedBranches);

        // Fetch completely filtered list from DB: 4th-year, 2021+, meets minCgpa, and matches allowed branches
        List<Student> eligibleStudents = studentRepository.findEligibleStudentsForDriveWithBranches(
                drive.getMinimumCgpa(), branchSet);

        int eligible = 0;
        List<EligibleDrive> toInsert = new ArrayList<>();

        for (Student student : eligibleStudents) {
            if (student.getRegdno() == null || student.getRegdno().isBlank()) {
                continue;
            }

            if (!eligibleDriveRepository.existsByRegdnoAndDriveId(student.getRegdno(), drive.getId())) {
                toInsert.add(EligibleDrive.builder()
                        .regdno(student.getRegdno())
                        .drive(drive)
                        .build());
                eligible++;
            }
        }

        if (!toInsert.isEmpty()) {
            saveEligibleDrivesBatch(toInsert);
        }

        log.info("Drive {} eligibility done — eligible 4th-year students inserted: {}",
                drive.getId(), eligible);
    }

    /**
     * Reads allowed branches from drive_jd.allowed_branches (pipe-separated).
     * Returns empty list if no JD or no branches set → means ALL branches allowed.
     */
    private List<String> getAllowedBranches(Long driveId) {
        return driveJDRepository.findByDriveIdWithSteps(driveId)
                .map(jd -> {
                    String branches = jd.getAllowedBranches();
                    if (branches == null || branches.isBlank()) return List.<String>of();
                    return Arrays.stream(branches.split("\\|"))
                            .map(String::trim)
                            .map(String::toUpperCase)
                            .filter(s -> !s.isBlank())
                            .collect(Collectors.toList());
                })
                .orElse(List.of());
    }

    // ── Student dashboard ─────────────────────────────────────────────────────

    /**
     * Returns eligible drives for a student — instant DB query.
     * Includes a real-time branch check via JPQL to filter out drives
     * from other branches that might have been populated incorrectly.
     */
    @Transactional(readOnly = true)
    public List<Drive> getEligibleDrivesForStudent(String regdno) {
        String studentBranchCode = studentRepository.findById(regdno)
                .map(Student::getBranchCode)
                .map(String::trim)
                .map(String::toUpperCase)
                .orElse("");

        return eligibleDriveRepository
                .findEligibleDrivesForStudent(regdno, studentBranchCode)
                .stream()
                .map(EligibleDrive::getDrive)
                .toList();
    }

    // ── Admin view ────────────────────────────────────────────────────────────

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
     * Returns null if BPUT has no data or call fails.
     */
    private BigDecimal fetchFromBput(Student student) {
        String dob = sessionCalculator.normalizeDob(student.getDob());
        String startSession = sessionCalculator.calculateStartSession(student.getAdmissionYear());
        String endSession = sessionCalculator.calculateEndSession(student.getAdmissionYear());

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