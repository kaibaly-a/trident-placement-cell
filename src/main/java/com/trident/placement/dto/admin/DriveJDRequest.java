package com.trident.placement.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * Admin sends this to create or update a DriveJD.
 * Drive ID comes from the URL path — not included here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveJDRequest {

    // ── Role & Job Info ───────────────────────────────────────────────────────
    @Size(max = 255)
    private String jobLocation;

    @Size(max = 50)
    private String employmentType;   // Full Time | Internship | Part Time | Contract

    @Size(max = 50)
    private String workMode;         // On-Site | Remote | Hybrid

    @Size(max = 100)
    private String vacancies;

    @Size(max = 255)
    private String serviceAgreement;

    @Size(max = 255)
    private String joining;

    // ── Eligibility ───────────────────────────────────────────────────────────
    @Size(max = 20)
    private String cgpaCutoff;       // e.g. "6.5" or "No Cutoff"

    private Boolean backlogsAllowed;

    private List<String> allowedBranches;   // ["CSE", "ETC", "EEE"]

    private List<String> allowedCourses;    // ["B.Tech", "MCA"]

    @Size(max = 100)
    private String batch;

    // ── Company & Role Content ────────────────────────────────────────────────
    @NotBlank(message = "About company is required")
    @Size(max = 2000)
    private String aboutCompany;

    @Size(max = 255)
    private String website;

    @Size(max = 255)
    private String headquarters;

    @NotBlank(message = "Role overview is required")
    @Size(max = 2000)
    private String roleOverview;

    @Size(max = 20, message = "Maximum 20 skills allowed")
    private List<String> requiredSkills;

    @Size(max = 20, message = "Maximum 20 responsibilities allowed")
    private List<String> keyResponsibilities;

    @Size(max = 10, message = "Maximum 10 why-join points allowed")
    private List<String> whyJoin;

    // ── Selection Process ─────────────────────────────────────────────────────
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