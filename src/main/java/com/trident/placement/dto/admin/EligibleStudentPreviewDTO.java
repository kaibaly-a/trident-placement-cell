package com.trident.placement.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one eligible student shown in the admin
 * "Review & Publish" step before a drive goes OPEN.
 *
 * Admin can select/deselect individual students before publishing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibleStudentPreviewDTO {

    private String regdno;
    private String name;
    private String branchCode;
    private String course;
    private Long   degreeYop;

    // Career marks — shown so admin can visually verify eligibility
    private Double tenthPercentage;
    private Double twelfthPercentage;
    private Double diplomaPercentage;
    private Double graduationPercentage;
}