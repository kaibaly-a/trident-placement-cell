package com.trident.placement.service;

import com.trident.placement.dto.ApplicationDTO;
import com.trident.placement.entity.Application;
import com.trident.placement.entity.Drive;
import com.trident.placement.entity.Student;
import com.trident.placement.enums.ApplicationStatus;
import com.trident.placement.enums.DriveStatus;
import com.trident.placement.repository.ApplicationRepository;
import com.trident.placement.repository.DriveRepository;
import com.trident.placement.repository.EligibleDriveRepository;
import com.trident.placement.repository.StudentCgpaRepository;
import com.trident.placement.repository.StudentRepository;
import com.trident.placement.util.BranchCodeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository  applicationRepository;
    private final StudentRepository      studentRepository;
    private final DriveRepository        driveRepository;
    private final EligibleDriveRepository eligibleDriveRepository;
    private final StudentCgpaRepository  studentCgpaRepository;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yy");

    @Override
    public List<ApplicationDTO> getStudentApplications(String regdno) {
        return applicationRepository
                .findByRegdnoOrderByAppliedDateDesc(regdno)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ApplicationDTO> getStudentApplicationsByStatus(String regdno, String status) {
        ApplicationStatus appStatus =
                ApplicationStatus.valueOf(status.toUpperCase());
        return applicationRepository
                .findByRegdnoAndStatus(regdno, appStatus)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ApplicationDTO applyToDrive(String frontendRegdno, Long driveId) {

        // ── 1. Resolve the authenticated student from DB ───────────────────────
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Student student = studentRepository.findByMsUserPrincipalName(email)
                .or(() -> studentRepository.findByEmail(email))
                .orElseThrow(() -> new RuntimeException("Authenticated student not found in DB"));

        String actualRegdno = student.getRegdno();

        // ── 2. Prevent duplicate applications ─────────────────────────────────
        if (applicationRepository.existsApplication(actualRegdno, driveId)) {
            throw new RuntimeException("You have already applied to this drive");
        }

        // ── 3. Load the drive ─────────────────────────────────────────────────
        Drive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new RuntimeException("Drive not found"));

        // ── 4. Eligibility check (pre-computed table first; real-time fallback) ─
        boolean eligible = eligibleDriveRepository.existsByRegdnoAndDriveId(actualRegdno, driveId);

        if (!eligible) {
            log.debug("Student {} not in ELIGIBLE_DRIVES for drive {}. Running real-time check...",
                    actualRegdno, driveId);
            eligible = isEligibleRealTime(student, drive);

            if (eligible) {
                // Backfill the pre-computed table so future checks are instant
                log.info("Real-time check PASSED for student={} drive={}. Backfilling ELIGIBLE_DRIVES.",
                        actualRegdno, driveId);
            } else {
                String reason = buildIneligibilityReason(student, drive);
                throw new RuntimeException(reason);
            }
        }

        // ── 5. Drive must be OPEN and deadline not passed ─────────────────────
        if (drive.getStatus() != DriveStatus.OPEN) {
            throw new RuntimeException("This drive is currently closed");
        }
        if (drive.getLastDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("Application deadline has passed for this drive");
        }

        // ── 6. Save application ───────────────────────────────────────────────
        Application application = Application.builder()
                .regdno(actualRegdno)
                .drive(drive)
                .appliedDate(LocalDate.now())
                .status(ApplicationStatus.APPLIED)
                .build();

        log.info("Application submitted: student={} drive={} ({})",
                actualRegdno, driveId, drive.getCompanyName());
        return toDTO(applicationRepository.save(application));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Real-time eligibility check — used as a fallback when ELIGIBLE_DRIVES
     * hasn't been populated yet (e.g., async job still pending or failed).
     *
     * Checks:
     *  1. Student is in their final year (ADMISSIONYEAR ≥ 3 years ago)
     *  2. Student's branch is in the drive's allowed branch list
     *  3. Student's CGPA meets the drive minimum
     */
    private boolean isEligibleRealTime(Student student, Drive drive) {
        // 1. Year check: admission year must be >= 3 years before current year
        int currentYear = LocalDate.now().getYear();
        int admissionYear;
        try {
            admissionYear = Integer.parseInt(student.getAdmissionYear().trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid ADMISSIONYEAR '{}' for student {}", student.getAdmissionYear(), student.getRegdno());
            return false;
        }
        if ((currentYear - admissionYear) < 3) {
            log.debug("Student {} not in final year (admissionYear={}, currentYear={})",
                    student.getRegdno(), admissionYear, currentYear);
            return false;
        }

        // 2. Branch check
        if (drive.getBranches() != null && !drive.getBranches().isEmpty()) {
            String studentBranch = student.getBranchCode() != null
                    ? student.getBranchCode().trim().toUpperCase() : "";
            boolean branchOk = BranchCodeUtils.normalizeList(drive.getBranches())
                    .contains(studentBranch);
            if (!branchOk) {
                log.debug("Student {} branch '{}' not in drive {} allowed branches {}",
                        student.getRegdno(), studentBranch, drive.getId(), drive.getBranches());
                return false;
            }
        }

        // 3. CGPA check
        var cgpaRecord = studentCgpaRepository.findByRegdno(student.getRegdno());
        if (cgpaRecord.isEmpty()) {
            log.debug("Student {} has no CGPA record — cannot verify CGPA eligibility", student.getRegdno());
            return false;
        }
        BigDecimal studentCgpa = cgpaRecord.get().getCgpa();
        if (drive.getMinimumCgpa() != null && studentCgpa.compareTo(drive.getMinimumCgpa()) < 0) {
            log.debug("Student {} CGPA {} < drive {} minimum {}", student.getRegdno(),
                    studentCgpa, drive.getId(), drive.getMinimumCgpa());
            return false;
        }

        return true;
    }

    /**
     * Builds a human-readable rejection reason for the student.
     */
    private String buildIneligibilityReason(Student student, Drive drive) {
        int currentYear = LocalDate.now().getYear();
        try {
            int admissionYear = Integer.parseInt(student.getAdmissionYear().trim());
            if ((currentYear - admissionYear) < 3) {
                return "You are not eligible: this drive is open to final-year students only.";
            }
        } catch (NumberFormatException ignored) {}

        if (drive.getBranches() != null && !drive.getBranches().isEmpty()) {
            String studentBranch = student.getBranchCode() != null
                    ? student.getBranchCode().trim().toUpperCase() : "UNKNOWN";
            if (!BranchCodeUtils.normalizeList(drive.getBranches()).contains(studentBranch)) {
                return "You are not eligible: your branch (" + studentBranch +
                       ") is not in the list of eligible branches for this drive (" +
                       drive.getBranches() + ").";
            }
        }

        return "You are not eligible: your CGPA does not meet the minimum requirement of " +
               drive.getMinimumCgpa() + " for this drive.";
    }

    private ApplicationDTO toDTO(Application app) {
        return ApplicationDTO.builder()
                .id(app.getId())
                .regdno(app.getRegdno())
                .driveId(app.getDrive().getId())
                .companyName(app.getDrive().getCompanyName())
                .role(app.getDrive().getRole())
                .appliedDate(app.getAppliedDate().format(DATE_FMT))
                .status(app.getStatus().name())
                .build();
    }
}