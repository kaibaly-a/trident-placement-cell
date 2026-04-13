package com.trident.placement.controller;

import com.trident.placement.dto.ApiResponse;
import com.trident.placement.dto.admin.AdminApplicationResponse;
import com.trident.placement.dto.admin.ApplicationStatusUpdateRequest;
import com.trident.placement.service.AdminApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Admin Application Management
 *
 * GET    /api/admin/applications                          → All applications
 * GET    /api/admin/applications?status=SHORTLISTED       → Filter by status
 * GET    /api/admin/applications/drive/{driveId}          → By drive
 * GET    /api/admin/applications/drive/{driveId}?status=  → By drive + status
 * PATCH  /api/admin/applications/{id}/status              → Update status
 * GET    /api/admin/applications/export                   → Export all as CSV
 * GET    /api/admin/applications/export?driveId={id}      → Export by drive as CSV
 */
@RestController
@RequestMapping("/api/admin/applications")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminApplicationController {

    private final AdminApplicationService adminApplicationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminApplicationResponse>>> getAllApplications(
            @RequestParam(required = false) String status) {

        List<AdminApplicationResponse> apps = (status != null && !status.isBlank())
                ? adminApplicationService.getApplicationsByStatus(status)
                : adminApplicationService.getAllApplications();

        return ResponseEntity.ok(ApiResponse.ok(apps));
    }

    @GetMapping("/drive/{driveId}")
    public ResponseEntity<ApiResponse<List<AdminApplicationResponse>>> getByDrive(
            @PathVariable Long driveId,
            @RequestParam(required = false) String status) {

        List<AdminApplicationResponse> apps = (status != null && !status.isBlank())
                ? adminApplicationService.getApplicationsByDriveAndStatus(driveId, status)
                : adminApplicationService.getApplicationsByDrive(driveId);

        return ResponseEntity.ok(ApiResponse.ok(apps));
    }

    @PatchMapping("/{applicationId}/status")
    public ResponseEntity<ApiResponse<AdminApplicationResponse>> updateStatus(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationStatusUpdateRequest request) {

        AdminApplicationResponse updated =
                adminApplicationService.updateApplicationStatus(applicationId, request);

        return ResponseEntity.ok(
                ApiResponse.ok("Application status updated to " + updated.getStatus(), updated));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) Long driveId) {

        byte[] csvBytes = adminApplicationService.exportApplicationsAsCsv(driveId);

        String filename = "applications_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) +
                ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }
}