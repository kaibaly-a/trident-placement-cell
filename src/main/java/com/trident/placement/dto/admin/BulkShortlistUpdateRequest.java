package com.trident.placement.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * Request body for PATCH /api/admin/drives/{driveId}/shortlist/bulk
 * Updates multiple students' status for one elimination round in one request.
 */
@Data
public class BulkShortlistUpdateRequest {

    /** Dynamic round name — must match one of the drive's elimination round names. */
    @NotBlank(message = "roundName is required")
    private String roundName;

    /** PENDING, PASSED, or FAILED */
    @NotBlank(message = "status is required")
    @Pattern(regexp = "PENDING|PASSED|FAILED", message = "status must be PENDING, PASSED, or FAILED")
    private String status;

    /** Application IDs to update. Max 1000 per request to prevent abuse. */
    @NotEmpty(message = "applicationIds must not be empty")
    private List<Long> applicationIds;

    /** Optional batch remarks applied to all updated records. */
    private String remarks;

    /** If true, sends in-app notifications to all affected students. */
    private boolean notify = false;
}
