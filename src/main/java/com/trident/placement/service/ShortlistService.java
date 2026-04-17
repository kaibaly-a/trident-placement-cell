package com.trident.placement.service;

import com.trident.placement.dto.admin.BulkShortlistResult;
import com.trident.placement.dto.admin.BulkShortlistUpdateRequest;
import com.trident.placement.dto.admin.ShortlistStatusResponse;
import com.trident.placement.dto.admin.ShortlistSummaryResponse;
import com.trident.placement.dto.admin.ShortlistUpdateRequest;
import com.trident.placement.dto.admin.ShortlistUpdateResponse;

import java.util.List;

public interface ShortlistService {

    /**
     * Returns per-student, per-round status for a drive.
     * Optionally filtered by roundName and/or status.
     * Paginated: page is 0-indexed.
     */
    ShortlistStatusResponse getStatus(Long driveId, String roundName, String status, int page, int size);

    /**
     * Updates a single student's status for one elimination round.
     * Validates that roundName is a configured elimination round for this drive.
     * Optionally sends an in-app notification.
     */
    ShortlistUpdateResponse updateSingle(Long driveId, Long applicationId,
                                         ShortlistUpdateRequest request, String updatedByEmail);

    /**
     * Updates multiple students' status for one elimination round in one call.
     * Validates that roundName is a configured elimination round for this drive.
     * Max 1000 applicationIds per request.
     * Optionally sends in-app notifications.
     */
    BulkShortlistResult updateBulk(Long driveId, BulkShortlistUpdateRequest request,
                                    String updatedByEmail);

    /**
     * Triggers in-app notifications for specific applications in a round.
     * Only sends if the status is PASSED or FAILED (not PENDING).
     */
    BulkShortlistResult sendBulkNotifications(Long driveId, com.trident.placement.dto.admin.BulkShortlistNotifyRequest request);

    /**
     * Returns round-by-round pass/fail/pending counts and pass rates for a drive.
     */
    ShortlistSummaryResponse getSummary(Long driveId);

    /**
     * Returns CSV bytes for export.
     * If roundName is provided, exports only that round's data.
     * If status is provided (e.g. "PASSED"), filters to only those rows.
     */
    byte[] exportCsv(Long driveId, String roundName, String status);

    /**
     * Returns the dynamic elimination round names configured for a drive,
     * derived from DriveJD.selectionSteps where eliminationRound = true.
     * Names are normalised: "Aptitude Test" → "APTITUDE_TEST".
     */
    List<String> getAvailableRounds(Long driveId);

    /**
     * Returns a paginated list of notifications for a specific student
     */
    com.trident.placement.dto.student.StudentNotificationResponse getStudentNotifications(String regdno, int page, int pageSize);
}
