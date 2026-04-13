package com.trident.placement.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight student record for admin list view.
 * GET /api/admin/students
 *
 * Full profile uses existing StudentDTO from the student module.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentSummaryDTO {
    private String regdno;
    private String name;
    private String email;
    private String branch;
    private String course;
    private String admissionYear;
    private Long degreeYop;
    private String status;          // student's status field from STUDENT table
    private long totalApplications; // how many drives they applied to
    private long placedCount;       // applications with APPROVED status
}