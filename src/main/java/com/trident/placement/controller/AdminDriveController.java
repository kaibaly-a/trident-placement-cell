package com.trident.placement.controller;

import com.trident.placement.dto.ApiResponse;
import com.trident.placement.dto.admin.AdminDriveResponse;
import com.trident.placement.dto.admin.DriveCreateRequest;
import com.trident.placement.dto.admin.DriveUpdateRequest;
import com.trident.placement.repository.AdminDriveRepository;
import com.trident.placement.repository.EligibleDriveRepository;
import com.trident.placement.service.AdminDriveService;
import com.trident.placement.service.CgpaEligibilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin Drive Management
 *
 * All endpoints require ROLE_ADMIN.
 * @PreAuthorize is a second layer of defence in addition to SecurityConfig.
 *
 * GET    /api/admin/drives              → All drives with applicant counts
 * GET    /api/admin/drives/{id}         → Single drive with counts
 * POST   /api/admin/drives              → Create new drive
 * PUT    /api/admin/drives/{id}         → Update drive (partial update)
 * DELETE /api/admin/drives/{id}         → Delete drive (only if no applications)
 * PATCH  /api/admin/drives/{id}/status  → Toggle OPEN ↔ CLOSED
 */
@RestController
@RequestMapping("/api/admin/drives")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminDriveController {

    private final AdminDriveService       adminDriveService;
    private final CgpaEligibilityService  cgpaEligibilityService;
    private final EligibleDriveRepository eligibleDriveRepository;
    private final AdminDriveRepository    adminDriveRepository;
    private final com.trident.placement.service.AdminApplicationService adminApplicationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminDriveResponse>>> getAllDrives() {
        List<AdminDriveResponse> drives = adminDriveService.getAllDrivesWithCounts();
        return ResponseEntity.ok(ApiResponse.ok(drives));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminDriveResponse>> getDriveById(
            @PathVariable Long id) {
        AdminDriveResponse drive = adminDriveService.getDriveById(id);
        return ResponseEntity.ok(ApiResponse.ok(drive));
    }

    /**
     * Frontend fallback endpoint for applications by drive
     * GET /api/admin/drives/{id}/applications
     */
    @GetMapping("/{id}/applications")
    public ResponseEntity<ApiResponse<List<com.trident.placement.dto.admin.AdminApplicationResponse>>> getApplicationsForDrive(
            @PathVariable Long id,
            @RequestParam(required = false) String status) {
        
        List<com.trident.placement.dto.admin.AdminApplicationResponse> apps = (status != null && !status.isBlank())
                ? adminApplicationService.getApplicationsByDriveAndStatus(id, status)
                : adminApplicationService.getApplicationsByDrive(id);
        
        return ResponseEntity.ok(ApiResponse.ok(apps));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminDriveResponse>> createDrive(
            @Valid @RequestBody DriveCreateRequest request) {
        AdminDriveResponse created = adminDriveService.createDrive(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Drive created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminDriveResponse>> updateDrive(
            @PathVariable Long id,
            @Valid @RequestBody DriveUpdateRequest request) {
        AdminDriveResponse updated = adminDriveService.updateDrive(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Drive updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDrive(@PathVariable Long id) {
        adminDriveService.deleteDrive(id);
        return ResponseEntity.ok(ApiResponse.ok("Drive deleted successfully", null));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminDriveResponse>> toggleStatus(
            @PathVariable Long id) {
        AdminDriveResponse updated = adminDriveService.toggleDriveStatus(id);
        return ResponseEntity.ok(
                ApiResponse.ok("Drive status changed to " + updated.getStatus(), updated));
    }

    @GetMapping("/{id}/eligible-students")
    public ResponseEntity<ApiResponse<List<com.trident.placement.dto.admin.EligibleStudentPreviewDTO>>> getEligibleStudents(
            @PathVariable Long id) {
        List<com.trident.placement.dto.admin.EligibleStudentPreviewDTO> students =
                adminDriveService.getEligibleStudentPreviews(id);
        return ResponseEntity.ok(ApiResponse.ok(students));
    }

    /**
     * PATCH /api/admin/drives/{id}/publish
     *
     * Publishes a DRAFT drive to OPEN.
     * Body: { "selectedRegdnos": ["2201289102", "2201289080", ...] }
     * Only the selected students will see this drive in their dashboard.
     */
    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<AdminDriveResponse>> publishDrive(
            @PathVariable Long id,
            @RequestBody com.trident.placement.dto.admin.PublishDriveRequest request) {
        AdminDriveResponse published = adminDriveService.publishDrive(id, request);
        return ResponseEntity.ok(ApiResponse.ok(
                "Drive published successfully to " +
                (request.getSelectedRegdnos() != null ? request.getSelectedRegdnos().size() : 0) +
                " student(s)", published));
    }

    /**
     * POST /api/admin/drives/{id}/recompute-eligibility
     *
     * Clears and re-runs the eligibility assignment for a specific drive.
     * Use this to fix old drives that have no entries in ELIGIBLE_DRIVES
     * (e.g., drives created before branches were correctly set).
     */
    @PostMapping("/{id}/recompute-eligibility")
    public ResponseEntity<ApiResponse<String>> recomputeEligibility(
            @PathVariable Long id) {
        log.info("Admin triggered eligibility recompute for drive id={}", id);
        eligibleDriveRepository.deleteByDriveId(id);
        cgpaEligibilityService.scheduleAssignEligibleStudentsAfterCommit(id);
        return ResponseEntity.ok(
                ApiResponse.ok("Eligibility recompute triggered for drive " + id +
                               ". Eligible students will be updated shortly.", null));
    }

    /**
     * POST /api/admin/drives/recompute-all-eligibility
     *
     * Wipes ELIGIBLE_DRIVES for ALL drives and re-runs the assignment.
     * Use this once to fix all drives that were created before branches were set.
     * The job runs asynchronously — returns immediately.
     */
    @PostMapping("/recompute-all-eligibility")
    public ResponseEntity<ApiResponse<String>> recomputeAllEligibility() {
        List<Long> driveIds = adminDriveRepository.findAllIds();
        log.info("Admin triggered FULL eligibility recompute for {} drives", driveIds.size());

        for (Long driveId : driveIds) {
            eligibleDriveRepository.deleteByDriveId(driveId);
            cgpaEligibilityService.scheduleAssignEligibleStudentsAfterCommit(driveId);
        }

        return ResponseEntity.ok(
                ApiResponse.ok("Full eligibility recompute triggered for " + driveIds.size() +
                               " drive(s). Students will be updated shortly.", null));
    }
}

