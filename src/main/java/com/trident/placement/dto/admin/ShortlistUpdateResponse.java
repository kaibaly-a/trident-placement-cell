package com.trident.placement.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response for single student shortlist update operations.
 * Returned by PATCH /api/admin/drives/{driveId}/shortlist/{applicationId}
 */
@Data
@Builder
public class ShortlistUpdateResponse {
    private Long id;
    private Long applicationId;
    private String regdno;
    private String round;
    private String status;
    private String remarks;
    private LocalDateTime decidedAt;
    private LocalDateTime updatedAt;
    private boolean notificationSent;
}
