package com.trident.placement.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request body for PATCH /api/admin/drives/{driveId}/shortlist/{applicationId}
 * Updates a single student's status for one elimination round.
 */
@Data
public class ShortlistUpdateRequest {

    /** Dynamic round name — must match one of the drive's elimination round names. */
    @NotBlank(message = "roundName is required")
    private String roundName;

    /** PENDING, PASSED, or FAILED */
    @NotBlank(message = "status is required")
    @Pattern(regexp = "PENDING|PASSED|FAILED", message = "status must be PENDING, PASSED, or FAILED")
    private String status;

    /** Optional feedback remarks for the student. */
    private String remarks;

    /** If true, sends an in-app notification to the student after update. */
    private boolean notify = false;
}
