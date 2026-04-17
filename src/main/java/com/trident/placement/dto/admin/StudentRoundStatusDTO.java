package com.trident.placement.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Per-student row in the shortlist status view.
 * Contains student info and a map of round → status.
 */
@Data
@Builder
public class StudentRoundStatusDTO {

    private Long applicationId;
    private String regdno;
    private String studentName;
    private String studentEmail;
    private String branch;
    private String appliedDate;

    /**
     * Map of round name → status string.
     * Status is "PENDING", "PASSED", "FAILED", or null (not yet entered for that round).
     *
     * Example: { "APTI": "PASSED", "DSA": "PENDING", "TECHNICAL": null }
     */
    private Map<String, String> roundStatus;
}
