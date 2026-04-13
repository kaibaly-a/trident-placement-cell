package com.trident.placement.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Application response for admin view — includes student details
 * alongside drive and status info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminApplicationResponse {
    private Long applicationId;

    // Student info
    private String regdno;
    private String studentName;
    private String studentEmail;
    private String branch;
    private String course;

    // Drive info
    private Long driveId;
    private String companyName;
    private String driveRole;
    private String driveType;

    // Application info
    private String status;          // APPLIED, SHORTLISTED, APPROVED, REJECTED
    private String appliedDate;     // formatted dd-MM-yyyy
    private String updatedAt;       // formatted dd-MM-yyyy HH:mm
}