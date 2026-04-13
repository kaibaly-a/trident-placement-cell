package com.trident.placement.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for PATCH /api/admin/applications/{applicationId}/status
 * Admin updates the status of a single application.
 *
 * Valid status values: APPLIED, SHORTLISTED, APPROVED, REJECTED
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    private String status;
}