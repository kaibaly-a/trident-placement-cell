package com.trident.placement.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Returned to both admin (preview) and students (drive detail page).
 * Includes parent Drive fields so the frontend only needs one API call.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveJDResponse {

    // ── Parent Drive fields ───────────────────────────────────────────────────
    private Long   driveId;
    private String companyName;
    private String role;
    private String driveType;          // ON_CAMPUS / OFF_CAMPUS / POOL
    private BigDecimal lpaPackage;
    private BigDecimal minimumCgpa;
    private String lastDate;           // formatted dd-MM-yyyy
    private String driveStatus;        // OPEN / CLOSED

    // ── JD: Role & Job Info ───────────────────────────────────────────────────
    private String jobLocation;
    private String employmentType;
    private String workMode;
    private String vacancies;
    private String serviceAgreement;
    private String joining;

    // ── JD: Eligibility ───────────────────────────────────────────────────────
    private String       cgpaCutoff;
    private Boolean      backlogsAllowed;
    private List<String> allowedBranches;
    private List<String> allowedCourses;
    private String       batch;

    // ── JD: Content ───────────────────────────────────────────────────────────
    private String       aboutCompany;
    private String       website;
    private String       headquarters;
    private String       roleOverview;
    private List<String> requiredSkills;
    private List<String> keyResponsibilities;
    private List<String> whyJoin;

    // ── JD: Selection Process ─────────────────────────────────────────────────
    private List<SelectionStepResponse> selectionProcess;

    /** Flag so frontend knows whether JD has been filled yet. */
    private boolean jdExists;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SelectionStepResponse {
        private String  description;
        private boolean eliminationRound;
    }
}