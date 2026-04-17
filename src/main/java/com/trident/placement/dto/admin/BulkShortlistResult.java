package com.trident.placement.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response for PATCH /api/admin/drives/{driveId}/shortlist/bulk
 * Reports how many records were updated, skipped, and notified.
 */
@Data
@Builder
public class BulkShortlistResult {

    private boolean success;
    private int updated;
    private int failed;
    private int notificationsSent;
    private int notificationsFailed;

    /** Details of records that could not be processed. */
    private List<FailedRecord> failedRecords;

    @Data
    @Builder
    public static class FailedRecord {
        private Long applicationId;
        private String reason;
    }
}
