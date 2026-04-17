package com.trident.placement.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response for GET /api/admin/drives/{driveId}/shortlist/status
 * Returns student list with their per-round statuses plus pagination and available rounds.
 */
@Data
@Builder
public class ShortlistStatusResponse {

    /** List of students with their round statuses. */
    private List<StudentRoundStatusDTO> data;

    /** Pagination metadata. */
    private long total;
    private int page;
    private int perPage;
    private int totalPages;

    /**
     * Dynamic round names configured for this drive.
     * Extracted from DriveJD selectionProcess where eliminationRound = true.
     */
    private List<String> availableRounds;
}
