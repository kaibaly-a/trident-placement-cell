package com.trident.placement.service;

import com.trident.placement.dto.StudentDTO;
import com.trident.placement.dto.admin.AdminApplicationResponse;
import com.trident.placement.dto.admin.AdminStatsDTO;
import com.trident.placement.dto.admin.StudentSummaryDTO;
import com.trident.placement.entity.Application;
import com.trident.placement.entity.Student;
import com.trident.placement.enums.ApplicationStatus;
import com.trident.placement.enums.DriveStatus;
import com.trident.placement.repository.AdminApplicationRepository;
import com.trident.placement.repository.AdminDriveRepository;
import com.trident.placement.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminStudentServiceImpl implements AdminStudentService {

    private final StudentRepository studentRepository;
    private final AdminApplicationRepository adminApplicationRepository;
    private final AdminDriveRepository adminDriveRepository;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // ── Dashboard Stats ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AdminStatsDTO getAdminStats() {
        long totalStudents    = studentRepository.count();
        long totalDrives      = adminDriveRepository.count();
        long openDrives       = adminDriveRepository.countByStatus(DriveStatus.OPEN);
        long totalApplications = adminApplicationRepository.countAll();
        long placedStudents   = adminApplicationRepository
                                    .countByStatus(ApplicationStatus.APPROVED);
        long shortlisted      = adminApplicationRepository
                                    .countByStatus(ApplicationStatus.SHORTLISTED);

        return AdminStatsDTO.builder()
                .totalStudents(totalStudents)
                .totalDrives(totalDrives)
                .openDrives(openDrives)
                .totalApplications(totalApplications)
                .placedStudents(placedStudents)
                .shortlistedStudents(shortlisted)
                .build();
    }

    // ── Student List ──────────────────────────────────────────────────────────
// Before adding pagination
//    @Override
//    @Transactional(readOnly = true)
//    public List<StudentSummaryDTO> getAllStudents() {
//        List<Student> students = studentRepository.findAll();
//        return buildStudentSummaries(students);
//    }
    
    
 // After
    
    @Override
    @Transactional(readOnly = true)
    public Page<StudentSummaryDTO> getAllStudents(int page, int size) {

        int startRow = page * size;       // e.g. page=0 → startRow=0
        int endRow   = startRow + size;   // e.g. size=20 → endRow=20

        List<Student> students = studentRepository.findAllPaginated(startRow, endRow);
        long total = studentRepository.countAllStudents();

        List<StudentSummaryDTO> content = buildStudentSummaries(students);

        Pageable pageable = PageRequest.of(page, size);
        return new PageImpl<>(content, pageable, total);
    }
    

    @Override
    @Transactional(readOnly = true)
    public List<StudentSummaryDTO> searchStudents(String query) {
        if (query == null || query.isBlank()) {
            // Return first 50 students alphabetically when no query given
            List<Student> students = studentRepository.findAllPaginated(0, 50);
            return buildStudentSummaries(students);
        }

        // DB-level search — Oracle filters, only matching rows returned
        List<Student> students = studentRepository.searchStudents(query.trim());
        return buildStudentSummaries(students);
    }

    
// // AFTER — Oracle filters, only matching rows come back
//    @Override
//    @Transactional(readOnly = true)
//    public List<StudentSummaryDTO> searchStudents(String query) {
//        if (query == null || query.isBlank()) {
//            // getAllStudents() now returns Page — return first 50 as flat list for blank query
//            Pageable pageable = PageRequest.of(0, 50, Sort.by("name").ascending());
//            List<Student> students = studentRepository.findAll(pageable).getContent();
//            return buildStudentSummaries(students);
//        }
//        // DB-level search — Oracle filters, only matching rows returned
//        List<Student> students = studentRepository.searchStudents(query.trim());
//        return buildStudentSummaries(students);
//    }

    // ── Student Profile ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public StudentDTO getStudentProfile(String regdno) {
        Student student = studentRepository.findByRegdno(regdno)
                .orElseThrow(() -> new RuntimeException(
                        "Student not found with regdno: " + regdno));
        return toStudentDTO(student);
    }

    // ── Student Application History ───────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AdminApplicationResponse> getStudentApplicationHistory(String regdno) {
        // Validate student exists
        Student student = studentRepository.findByRegdno(regdno)
                .orElseThrow(() -> new RuntimeException(
                        "Student not found with regdno: " + regdno));

        List<Application> apps =
                adminApplicationRepository.findByRegdnoWithDrive(regdno);

        return apps.stream()
                .map(app -> toApplicationDTO(app, student))
                .collect(Collectors.toList());
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private List<StudentSummaryDTO> buildStudentSummaries(List<Student> students) {
        // Fetch application counts for all students in ONE query
        List<Object[]> countRows = adminApplicationRepository.findApplicationCountsPerStudent();

        // Build map: regdno → [totalApplications, approvedCount]
        Map<String, long[]> countMap = new HashMap<>();
        for (Object[] row : countRows) {
            String regdno  = (String) row[0];
            long total     = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long approved  = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            countMap.put(regdno, new long[]{total, approved});
        }

        return students.stream()
                .map(student -> {
                    long[] counts = countMap.getOrDefault(
                            student.getRegdno(), new long[]{0, 0});
                    return StudentSummaryDTO.builder()
                            .regdno(student.getRegdno())
                            .name(student.getName())
                            .email(student.getEmail())
                            .branch(student.getBranchCode())
                            .course(student.getCourse())
                            .admissionYear(student.getAdmissionYear())
                            .degreeYop(student.getDegreeYop())
                            .status(student.getStatus())
                            .totalApplications(counts[0])
                            .placedCount(counts[1])
                            .build();
                })
                .collect(Collectors.toList());
    }

    private AdminApplicationResponse toApplicationDTO(Application app, Student student) {
        return AdminApplicationResponse.builder()
                .applicationId(app.getId())
                .regdno(app.getRegdno())
                .studentName(student.getName())
                .studentEmail(student.getEmail())
                .branch(student.getBranchCode())
                .course(student.getCourse())
                .driveId(app.getDrive().getId())
                .companyName(app.getDrive().getCompanyName())
                .driveRole(app.getDrive().getRole())
                .driveType(app.getDrive().getDriveType().name())
                .status(app.getStatus().name())
                .appliedDate(app.getAppliedDate().format(DATE_FMT))
                .updatedAt(app.getUpdatedAt().format(DATETIME_FMT))
                .build();
    }

    private StudentDTO toStudentDTO(Student s) {
        return StudentDTO.builder()
                .regdno(s.getRegdno())
                .name(s.getName())
                .gender(s.getGender())
                .dob(s.getDob())
                .course(s.getCourse())
                .branchCode(s.getBranchCode())
                .admissionYear(s.getAdmissionYear())
                .degreeYop(s.getDegreeYop())
                .phno(s.getPhno())
                .email(s.getEmail())
                .studentType(s.getStudentType())
                .hostelier(s.getHostelier())
                .transportAvailed(s.getTransportAvailed())
                .status(s.getStatus())
                .batchId(s.getBatchId())
                .currentYear(s.getCurrentYear())
                .aadharno(s.getAadhaarno())
                .indortrng(s.getIndortrng())
                .plpoolm(s.getPlpoolm())
                .cfpaymode(s.getCfpaymode())
                .religion(s.getReligion())
                .msUserPrincipalName(s.getMsUserPrincipalName())
                .collegeName(s.getCollegeName())
                .build();
    }
}