package com.trident.placement.service;

import com.trident.placement.dto.admin.AdminApplicationResponse;
import com.trident.placement.dto.admin.ApplicationStatusUpdateRequest;
import com.trident.placement.entity.Application;
import com.trident.placement.entity.Student;
import com.trident.placement.enums.ApplicationStatus;
import com.trident.placement.repository.AdminApplicationRepository;
import com.trident.placement.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminApplicationServiceImpl implements AdminApplicationService {

    private final AdminApplicationRepository adminApplicationRepository;
    private final StudentRepository studentRepository;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AdminApplicationResponse> getAllApplications() {
        List<Application> apps = adminApplicationRepository.findAllWithDrive();
        Map<String, Student> studentMap = buildStudentMap(apps);
        return apps.stream()
                .map(app -> toDTO(app, studentMap.get(app.getRegdno())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminApplicationResponse> getApplicationsByDrive(Long driveId) {
        List<Application> apps = adminApplicationRepository.findByDriveId(driveId);
        Map<String, Student> studentMap = buildStudentMap(apps);
        return apps.stream()
                .map(app -> toDTO(app, studentMap.get(app.getRegdno())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminApplicationResponse> getApplicationsByStatus(String status) {
        ApplicationStatus appStatus = parseStatus(status);
        List<Application> apps = adminApplicationRepository.findByStatus(appStatus);
        Map<String, Student> studentMap = buildStudentMap(apps);
        return apps.stream()
                .map(app -> toDTO(app, studentMap.get(app.getRegdno())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminApplicationResponse> getApplicationsByDriveAndStatus(Long driveId,
                                                                           String status) {
        ApplicationStatus appStatus = parseStatus(status);
        List<Application> apps =
                adminApplicationRepository.findByDriveIdAndStatus(driveId, appStatus);
        Map<String, Student> studentMap = buildStudentMap(apps);
        return apps.stream()
                .map(app -> toDTO(app, studentMap.get(app.getRegdno())))
                .collect(Collectors.toList());
    }

    // ── Update Status ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AdminApplicationResponse updateApplicationStatus(Long applicationId,
                                                            ApplicationStatusUpdateRequest request) {
        Application app = adminApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException(
                        "Application not found with id: " + applicationId));

        ApplicationStatus newStatus = parseStatus(request.getStatus());
        ApplicationStatus oldStatus = app.getStatus();

        app.setStatus(newStatus);
        Application updated = adminApplicationRepository.save(app);

        log.info("Admin updated application id={} status: {} → {}",
                applicationId, oldStatus, newStatus);

        Student student = studentRepository.findByRegdno(app.getRegdno()).orElse(null);
        return toDTO(updated, student);
    }

    // ── Export CSV ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public byte[] exportApplicationsAsCsv(Long driveId) {
        List<Application> apps = driveId != null
                ? adminApplicationRepository.findByDriveId(driveId)
                : adminApplicationRepository.findAllWithDrive();

        Map<String, Student> studentMap = buildStudentMap(apps);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8);

        // CSV header
        writer.println("Application ID,Reg No,Student Name,Email,Branch,Course," +
                       "Company,Drive Role,Drive Type,Status,Applied Date,Updated At");

        // CSV rows
        for (Application app : apps) {
            Student student = studentMap.get(app.getRegdno());
            writer.printf("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                    app.getId(),
                    app.getRegdno(),
                    csvEscape(student != null ? student.getName() : ""),
                    csvEscape(student != null ? student.getEmail() : ""),
                    csvEscape(student != null ? student.getBranchCode() : ""),
                    csvEscape(student != null ? student.getCourse() : ""),
                    csvEscape(app.getDrive().getCompanyName()),
                    csvEscape(app.getDrive().getRole()),
                    app.getDrive().getDriveType().name(),
                    app.getStatus().name(),
                    app.getAppliedDate().format(DATE_FMT),
                    app.getUpdatedAt().format(DATETIME_FMT)
            );
        }

        writer.flush();
        return out.toByteArray();
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Batch-loads all students for a list of applications in ONE query.
     * Prevents N+1 — never call studentRepository inside a loop.
     */
    private Map<String, Student> buildStudentMap(List<Application> apps) {
        List<String> regdnos = apps.stream()
                .map(Application::getRegdno)
                .distinct()
                .collect(Collectors.toList());

        return studentRepository.findAllById(regdnos).stream()
                .collect(Collectors.toMap(Student::getRegdno, s -> s));
    }

    private ApplicationStatus parseStatus(String status) {
        try {
            return ApplicationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid status: '" + status +
                    "'. Valid values: APPLIED, SHORTLISTED, APPROVED, REJECTED");
        }
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        // Wrap in quotes if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private AdminApplicationResponse toDTO(Application app, Student student) {
        return AdminApplicationResponse.builder()
                .applicationId(app.getId())
                .regdno(app.getRegdno())
                .studentName(student != null ? student.getName() : "Unknown")
                .studentEmail(student != null ? student.getEmail() : "")
                .branch(student != null ? student.getBranchCode() : "")
                .course(student != null ? student.getCourse() : "")
                .driveId(app.getDrive().getId())
                .companyName(app.getDrive().getCompanyName())
                .driveRole(app.getDrive().getRole())
                .driveType(app.getDrive().getDriveType().name())
                .status(app.getStatus().name())
                .appliedDate(app.getAppliedDate().format(DATE_FMT))
                .updatedAt(app.getUpdatedAt().format(DATETIME_FMT))
                .build();
    }
}