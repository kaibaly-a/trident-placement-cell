package com.trident.placement.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Drive response for admin — includes applicant count per drive.
 * Richer than student-facing DriveDTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDriveResponse {
    private Long id;
    private String companyName;
    private String role;
    private String driveType;
    private BigDecimal lpaPackage;
    private BigDecimal minimumCgpa;
    private String lastDate; // formatted dd-MM-yyyy
    private String description;
    private String status; // OPEN / CLOSED
    private long totalApplicants; // total applications for this drive
    private long shortlistedCount; // applications with SHORTLISTED status
    private long selectedCount; // applications with APPROVED status
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private java.util.List<String> branches;

    private String eligibleCourse;
    private Long passoutYear;
}