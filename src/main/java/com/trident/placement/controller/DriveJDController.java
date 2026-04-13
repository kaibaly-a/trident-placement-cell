package com.trident.placement.controller;

import com.trident.placement.dto.ApiResponse;
import com.trident.placement.dto.DriveJDResponse;
import com.trident.placement.dto.admin.DriveJDRequest;
import com.trident.placement.service.DriveJDService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Drive JD Management
 *
 * ── Admin Endpoints (ROLE_ADMIN) ──────────────────────────────────────────────
 * POST   /api/admin/drives/{id}/jd        → Create JD for a drive
 * PUT    /api/admin/drives/{id}/jd        → Update existing JD
 * PATCH  /api/admin/drives/{id}/jd        → Upsert (create or update) — used by frontend
 * GET    /api/admin/drives/{id}/jd        → Get JD (admin preview, no eligibility check)
 * DELETE /api/admin/drives/{id}/jd        → Delete JD
 *
 * ── Student Endpoint (ROLE_STUDENT) ───────────────────────────────────────────
 * GET    /api/drives/{id}/jd?regdno={regdno}
 *           → Get JD only if student is eligible (4th year + CGPA meets cutoff).
 *             Alumni and ineligible students receive an error.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class DriveJDController {

    private final DriveJDService driveJDService;

    // ── Admin: Create ─────────────────────────────────────────────────────────

    @PostMapping("/api/admin/drives/{driveId}/jd")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriveJDResponse>> createJD(
            @PathVariable Long driveId,
            @Valid @RequestBody DriveJDRequest request) {

        DriveJDResponse response = driveJDService.createJD(driveId, request);
        log.info("Admin created JD for drive id={}", driveId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Job Description created successfully", response));
    }

    // ── Admin: Update ─────────────────────────────────────────────────────────

    @PutMapping("/api/admin/drives/{driveId}/jd")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriveJDResponse>> updateJD(
            @PathVariable Long driveId,
            @Valid @RequestBody DriveJDRequest request) {

        DriveJDResponse response = driveJDService.updateJD(driveId, request);
        log.info("Admin updated JD for drive id={}", driveId);
        return ResponseEntity.ok(
                ApiResponse.ok("Job Description updated successfully", response));
    }

    // ── Admin: Upsert (used by frontend "Save & Publish" button) ─────────────

    @PatchMapping("/api/admin/drives/{driveId}/jd")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriveJDResponse>> upsertJD(
            @PathVariable Long driveId,
            @Valid @RequestBody DriveJDRequest request) {

        DriveJDResponse response = driveJDService.upsertJD(driveId, request);
        log.info("Admin upserted JD for drive id={}", driveId);
        return ResponseEntity.ok(
                ApiResponse.ok("Job Description saved successfully", response));
    }

    // ── Admin: Get (preview — no eligibility check) ───────────────────────────

    @GetMapping("/api/admin/drives/{driveId}/jd")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DriveJDResponse>> getJDAdmin(
            @PathVariable Long driveId) {

        DriveJDResponse response = driveJDService.getJD(driveId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── Admin: Delete ─────────────────────────────────────────────────────────

    @DeleteMapping("/api/admin/drives/{driveId}/jd")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteJD(
            @PathVariable Long driveId) {

        driveJDService.deleteJD(driveId);
        log.info("Admin deleted JD for drive id={}", driveId);
        return ResponseEntity.ok(
                ApiResponse.ok("Job Description deleted successfully", null));
    }

    // ── Student: Get JD (eligibility enforced) ────────────────────────────────

    /**
     * GET /api/drives/{driveId}/jd?regdno={regdno}
     *
     * Returns the full JD only if the student is eligible for this drive.
     *
     * Eligibility = student appears in eligible_drives table, which is populated
     * only for 4th-year students (admissionYear >= 2021) whose stored CGPA meets
     * the drive's minimum CGPA requirement.
     *
     * Alumni and students who don't meet the CGPA cutoff receive an error
     * ("You are not eligible for this drive").
     */
    @GetMapping("/api/drives/{driveId}/jd")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<DriveJDResponse>> getJDStudent(
            @PathVariable Long driveId,
            @RequestParam String regdno) {

        DriveJDResponse response = driveJDService.getJDForStudent(driveId, regdno);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}