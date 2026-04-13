package com.trident.placement.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.trident.placement.dto.ApiResponse;
import com.trident.placement.dto.DashboardResponse;
import com.trident.placement.dto.DashboardStatsDTO;
import com.trident.placement.dto.DriveDTO;
import com.trident.placement.dto.StudentDTO;
import com.trident.placement.service.DriveService;
import com.trident.placement.service.StudentService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final StudentService studentService;
    private final DriveService driveService;

    /**
     * GET /api/dashboard/{regdno}
     *
     * Returns student profile, dashboard stats, and eligible drives.
     *
     * Eligible drives come from the eligible_drives table — pre-computed
     * when admin posted the drive. No BPUT call happens here.
     * Response is instant.
     */
    @GetMapping("/{regdno}")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @PathVariable String regdno) {
        try {
            StudentDTO student          = studentService.getStudentById(regdno);
            DashboardStatsDTO stats     = studentService.getDashboardStats(regdno);

            // ── Eligible drives from DB — no BPUT call ──────────────────────
            // Previously this needed dob/startSession/endSession as params.
            // Now it queries the eligible_drives table directly.
            List<DriveDTO> eligibleDrives = driveService.getEligibleDrives(regdno);

            DashboardResponse response = new DashboardResponse(student, stats, eligibleDrives);
            return ResponseEntity.ok(ApiResponse.ok(response));

        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/dashboard/stats/{regdno}
     * Returns only the stats counts for the dashboard header cards.
     */
    @GetMapping("/stats/{regdno}")
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getStats(
            @PathVariable String regdno) {
        try {
            DashboardStatsDTO stats = studentService.getDashboardStats(regdno);
            return ResponseEntity.ok(ApiResponse.ok(stats));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }
}