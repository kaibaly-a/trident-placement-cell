package com.trident.placement.controller;

import com.trident.placement.dto.ApiResponse;
import com.trident.placement.dto.admin.*;
import com.trident.placement.service.ShortlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin Shortlisting Controller
 *
 * All endpoints require ROLE_ADMIN or ROLE_TPO.
 *
 * GET    /api/admin/drives/{driveId}/shortlist/status    → Per-student round status (paginated)
 * GET    /api/admin/drives/{driveId}/shortlist/summary   → Round-wise counts and pass rates
 * GET    /api/admin/drives/{driveId}/shortlist/rounds    → Available elimination round names
 * GET    /api/admin/drives/{driveId}/shortlist/export    → CSV download
 * PATCH  /api/admin/drives/{driveId}/shortlist/{appId}   → Update single student's round status
 * PATCH  /api/admin/drives/{driveId}/shortlist/bulk      → Bulk update round status
 */
@RestController
@RequestMapping("/api/admin/drives/{driveId}/shortlist")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') or hasRole('TPO')")
public class ShortlistController {

    private final ShortlistService shortlistService;

    // ── 1. Status (paginated) ─────────────────────────────────────────────────

    /**
     * GET /api/admin/drives/{driveId}/shortlist/status
     *
     * Query params:
     *   round  - filter by round name (optional)
     *   status - filter by status: PENDING | PASSED | FAILED (optional)
     *   page   - 0-indexed page (default 0)
     *   size   - page size (default 50)
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ShortlistStatusResponse>> getStatus(
            @PathVariable Long driveId,
            @RequestParam(required = false) String round,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.debug("getStatus: driveId={} round={} status={} page={} size={}",
            driveId, round, status, page, size);
        ShortlistStatusResponse response = shortlistService.getStatus(driveId, round, status, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── 2. Summary ────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/drives/{driveId}/shortlist/summary
     *
     * Returns round-by-round pass/fail/pending counts and pass rates.
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ShortlistSummaryResponse>> getSummary(
            @PathVariable Long driveId) {

        ShortlistSummaryResponse response = shortlistService.getSummary(driveId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── 3. Available Rounds ───────────────────────────────────────────────────

    /**
     * GET /api/admin/drives/{driveId}/shortlist/rounds
     *
     * Returns dynamic elimination round names from the drive's JD configuration.
     * Frontend uses this to build round tabs dynamically.
     */
    @GetMapping("/rounds")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableRounds(
            @PathVariable Long driveId) {

        List<String> rounds = shortlistService.getAvailableRounds(driveId);
        return ResponseEntity.ok(ApiResponse.ok(rounds));
    }

    // ── 4. CSV Export ─────────────────────────────────────────────────────────

    /**
     * GET /api/admin/drives/{driveId}/shortlist/export
     *
     * Downloads shortlist data as CSV.
     * Query params:
     *   round  - filter by round name (optional; all rounds if omitted)
     *   status - filter by status e.g. "PASSED" (optional)
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable Long driveId,
            @RequestParam(required = false) String round,
            @RequestParam(required = false) String status) {

        byte[] csv = shortlistService.exportCsv(driveId, round, status);
        String filename = "shortlist_drive_" + driveId + "_" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    // ── 5. Update Single ──────────────────────────────────────────────────────

    /**
     * PATCH /api/admin/drives/{driveId}/shortlist/{applicationId}
     *
     * Updates one student's status for one elimination round.
     * Body: { roundName, status, comment, notify }
     */
    @PatchMapping("/{applicationId}")
    public ResponseEntity<ApiResponse<ShortlistUpdateResponse>> updateSingle(
            @PathVariable Long driveId,
            @PathVariable Long applicationId,
            @Valid @RequestBody ShortlistUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String email = resolveEmail(jwt);
        log.info("updateSingle: driveId={} appId={} round={} status={} by={}",
            driveId, applicationId, request.getRoundName(), request.getStatus(), email);

        ShortlistUpdateResponse response = shortlistService.updateSingle(
            driveId, applicationId, request, email);
        return ResponseEntity.ok(ApiResponse.ok("Status updated successfully", response));
    }

    // ── 6. Bulk Update ────────────────────────────────────────────────────────

    /**
     * PATCH /api/admin/drives/{driveId}/shortlist/bulk
     *
     * Updates multiple students' status for one round in one request.
     * Body: { roundName, status, applicationIds[], comment, notify }
     * Max 1000 application IDs per request.
     */
    @PatchMapping("/bulk")
    public ResponseEntity<ApiResponse<BulkShortlistResult>> updateBulk(
            @PathVariable Long driveId,
            @Valid @RequestBody BulkShortlistUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String email = resolveEmail(jwt);
        log.info("updateBulk: driveId={} round={} status={} count={} by={}",
            driveId, request.getRoundName(), request.getStatus(),
            request.getApplicationIds().size(), email);

        BulkShortlistResult result = shortlistService.updateBulk(driveId, request, email);
        String msg = "Bulk update complete — updated: " + result.getUpdated()
            + ", failed: " + result.getFailed();
        return ResponseEntity.ok(ApiResponse.ok(msg, result));
    }

    // ── 7. Bulk Notify ────────────────────────────────────────────────────────

    /**
     * POST /api/admin/drives/{driveId}/shortlist/notify
     *
     * Mass sends in-app notifications to students regarding their decisions.
     * Only sends to PASSED and FAILED students (PENDING is rejected).
     */
    @PostMapping("/notify")
    public ResponseEntity<ApiResponse<BulkShortlistResult>> sendBulkNotifications(
            @PathVariable Long driveId,
            @Valid @RequestBody BulkShortlistNotifyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String email = resolveEmail(jwt);
        log.info("triggerBulkNotify: driveId={} round={} count={} by={}",
            driveId, request.getRoundName(), request.getApplicationIds().size(), email);

        BulkShortlistResult result = shortlistService.sendBulkNotifications(driveId, request);
        String msg = "Bulk notification complete — sent: " + result.getNotificationsSent()
            + ", failed/already sent: " + result.getNotificationsFailed();
        return ResponseEntity.ok(ApiResponse.ok(msg, result));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts the admin's email from the Azure AD JWT.
     * Falls back to "unknown" if the claim is missing.
     */
    private String resolveEmail(Jwt jwt) {
        if (jwt == null) return "unknown";
        String email = jwt.getClaimAsString("preferred_username");
        if (email == null) {
            email = jwt.getClaimAsString("email");
        }
        return email != null ? email.toLowerCase() : "unknown";
    }
}
