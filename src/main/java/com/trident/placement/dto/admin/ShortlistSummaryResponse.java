package com.trident.placement.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response for GET /api/admin/drives/{driveId}/shortlist/summary
 * Provides round-wise pass/fail/pending counts and pass rates.
 */
@Data
@Builder
public class ShortlistSummaryResponse {

    private Long driveId;
    private String companyName;
    private long totalApplications;

    /** Dynamic round names extracted from drive JD. */
    private List<String> availableRounds;

    /**
     * Round-level breakdown.
     * Map key = round name (e.g., "APTI").
     * Map value = RoundStats object with counts and pass rate.
     */
    private Map<String, RoundStats> byRound;

    @Data
    @Builder
    public static class RoundStats {
        private long passed;
        private long failed;
        private long pending;
        private double passRate;   // percentage, e.g., 53.3
    }
}
