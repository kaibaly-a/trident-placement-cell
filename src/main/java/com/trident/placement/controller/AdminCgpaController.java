package com.trident.placement.controller;

import com.trident.placement.dto.ApiResponse;
import com.trident.placement.service.CgpaEligibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin CGPA Management
 *
 * POST /api/admin/cgpa/refresh-all     → Fetch CGPA for ALL students from BPUT
 * POST /api/admin/cgpa/refresh/{regdno}→ Refresh CGPA for ONE student
 * GET  /api/admin/cgpa/{regdno}        → View stored CGPA for a student
 */
@RestController
@RequestMapping("/api/admin/cgpa")
@RequiredArgsConstructor
@Slf4j
//@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("permitAll()")
public class AdminCgpaController {

    private final CgpaEligibilityService cgpaEligibilityService;

    /**
     * POST /api/admin/cgpa/refresh-all
     *
     * Admin clicks this button periodically (e.g., once per semester)
     * to fetch updated CGPAs for all students from BPUT.
     *
     * Runs ASYNCHRONOUSLY — API responds immediately with a message.
     * Progress can be monitored via logs.
     *
     * When to use:
     *  - Start of placement season
     *  - After semester results are published on BPUT
     *  - Before posting new drives
     */
    @PostMapping("/refresh-all")
    public ResponseEntity<ApiResponse<String>> refreshAllCgpa() {
        log.info("Admin triggered CGPA refresh for all students");

        // Runs in background thread — returns immediately
        cgpaEligibilityService.refreshAllStudentCgpa();

        return ResponseEntity.ok(ApiResponse.ok(
                "CGPA refresh started for all students. " +
                "This runs in the background — check logs for progress."
        ));
    }

    /**
     * POST /api/admin/cgpa/refresh/{regdno}
     *
     * Refresh CGPA for a single student.
     * Useful when a student's result was updated on BPUT after the last bulk refresh.
     */
    @PostMapping("/refresh/{regdno}")
    public ResponseEntity<ApiResponse<String>> refreshSingleCgpa(
            @PathVariable String regdno) {
        log.info("Admin triggered CGPA refresh for student: {}", regdno);

        cgpaEligibilityService.refreshSingleStudentCgpa(regdno);

        return ResponseEntity.ok(ApiResponse.ok(
                "CGPA refresh started for student: " + regdno
        ));
    }

    /**
     * GET /api/admin/cgpa/{regdno}
     *
     * View the currently stored CGPA for a student.
     * Shows when it was last fetched from BPUT.
     */
    @GetMapping("/{regdno}")
    public ResponseEntity<ApiResponse<CgpaEligibilityService.StudentCgpaInfo>>
    getStudentCgpa(@PathVariable String regdno) {

        CgpaEligibilityService.StudentCgpaInfo info =
                cgpaEligibilityService.getStoredCgpaInfo(regdno);

        return ResponseEntity.ok(ApiResponse.ok(info));
    }
}