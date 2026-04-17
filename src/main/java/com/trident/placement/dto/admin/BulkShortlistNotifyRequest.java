package com.trident.placement.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Request body for POST /api/admin/drives/{driveId}/shortlist/notify
 * Triggers bulk notifications for specific students in a specific round.
 */
@Data
public class BulkShortlistNotifyRequest {

    /** Dynamic round name — must match one of the drive's elimination round names. */
    @NotBlank(message = "roundName is required")
    private String roundName;

    /** Application IDs to notify. Max 1000 per request. */
    @NotEmpty(message = "applicationIds must not be empty")
    private List<Long> applicationIds;
}
