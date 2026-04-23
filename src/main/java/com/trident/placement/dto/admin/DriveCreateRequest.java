package com.trident.placement.dto.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Unified request body for POST /api/admin/drives
 *
 * Accepts the full payload from the frontend in one shot:
 * — Core drive fields (company, role, lpa, cgpa, branches …)
 * — Job Description fields (jobLocation, selectionProcess, skills …)
 *
 * JSON aliases are used so the frontend doesn't have to rename any key
 * (e.g. "lpa" or "lpaPackage" both work; "allowedBranches" or "branches" both
 * work).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveCreateRequest {

    // ── Core Drive fields ─────────────────────────────────────────────────────

    @NotBlank(message = "Company name is required")
    @Size(max = 200, message = "Company name must not exceed 200 characters")
    private String companyName;

    @NotBlank(message = "Job role is required")
    @Size(max = 150, message = "Job role must not exceed 150 characters")
    private String role;

    @NotBlank(message = "Drive type is required (ON_CAMPUS, OFF_CAMPUS, POOL)")
    private String driveType;

    /**
     * Frontend sends "lpa" — backend field is lpaPackage.
     * 
     * @JsonProperty / @JsonAlias covers both cases.
     */
    @NotNull(message = "LPA package is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "LPA package must be greater than 0")
    @Digits(integer = 3, fraction = 2, message = "LPA format: up to 3 digits and 2 decimal places")
    @JsonAlias({ "lpaPackage", "lpa" })
    private BigDecimal lpaPackage;

    @NotNull(message = "Minimum CGPA is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Minimum CGPA must be greater than 0")
    @DecimalMax(value = "10.0", message = "Minimum CGPA cannot exceed 10.0")
    @JsonAlias({ "minimumCgpa", "cgpaCutoff" })
    private BigDecimal minimumCgpa;

    @NotNull(message = "Last date is required")
    @Future(message = "Last date must be a future date")
    @JsonAlias({ "lastDate", "lastDateApplication" })
    private LocalDate lastDate;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    /**
     * Branch codes controlling which students are eligible.
     * Accepted keys: "branches", "allowedBranches", "eligibleBranches"
     */
    @NotNull(message = "Branches are required")
    @NotEmpty(message = "Select at least one branch for this drive")
    @JsonAlias({ "branches", "allowedBranches", "eligibleBranches" })
    private List<String> eligibleBranches;

    @Size(max = 50)
    private String eligibleCourse; // "B.TECH"

    private Long passoutYear; // 2026

    // ── Career Marks Criteria ─────────────────────────────────────────────────
    // All optional. Null = no minimum required for that field.
    // Set e.g. minTenthPercent=60.0 to require >= 60% in 10th.

    private BigDecimal minTenthPercent;
    private BigDecimal minTwelfthPercent;
    private BigDecimal minDiplomaPercent;
    private BigDecimal minGraduationPercent;

    // ── Job Description fields ────────────────────────────────────────────────
    // All optional — a shell JD is created even if these are absent.

    @Size(max = 255)
    private String jobLocation;

    @Size(max = 50)
    private String employmentType; // Full Time | Internship | Part Time | Contract

    @Size(max = 50)
    private String workMode; // On-Site | Remote | Hybrid

    @Size(max = 100)
    private String vacancies;

    @Size(max = 255)
    private String serviceAgreement;

    /** "joining" or "joiningInfo" — both are mapped. */
    @Size(max = 255)
    @JsonAlias({ "joining", "joiningInfo" })
    private String joining;

    /**
     * Human-readable cutoff string, e.g. "6.5" or "No Cutoff" (stored in JD for
     * display).
     */
    @Size(max = 20)
    @JsonAlias({ "cgpaCutoffDisplay" })
    private String cgpaCutoffDisplay;

    private Boolean backlogsAllowed;

    /** Allowed courses for display in JD (e.g. ["B.Tech", "MCA"]). */
    private List<String> allowedCourses;

    @Size(max = 100)
    private String batch;

    // ── Company & Role Content ─────────────────────────────────────────────────

    @Size(max = 2000)
    private String aboutCompany;

    @Size(max = 255)
    private String website;

    @Size(max = 255)
    private String headquarters;

    @Size(max = 2000)
    private String roleOverview;

    @Size(max = 20, message = "Maximum 20 skills allowed")
    private List<String> requiredSkills;

    @Size(max = 20, message = "Maximum 20 responsibilities allowed")
    private List<String> keyResponsibilities;

    @Size(max = 10, message = "Maximum 10 why-join points allowed")
    private List<String> whyJoin;

    // ── Selection Process ──────────────────────────────────────────────────────

    @Valid
    @Size(max = 10, message = "Maximum 10 selection steps allowed")
    private List<SelectionStepRequest> selectionProcess;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectionStepRequest {
        @NotBlank(message = "Step description is required")
        @Size(max = 255)
        private String description;
        private boolean eliminationRound;
    }
}