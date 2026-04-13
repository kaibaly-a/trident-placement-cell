package com.trident.placement.controller;

import com.trident.placement.dto.ApiResponse;
import com.trident.placement.dto.StudentDTO;
import com.trident.placement.dto.admin.AdminApplicationResponse;
import com.trident.placement.dto.admin.AdminStatsDTO;
import com.trident.placement.dto.admin.StudentSummaryDTO;
import com.trident.placement.service.AdminStudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin Student Management + Dashboard Stats
 *
 * GET  /api/admin/stats                            → Dashboard stats
 * GET  /api/admin/students                         → All students with application counts
 * GET  /api/admin/students/search?q={query}        → Search by name / regdno / branch / email
 * GET  /api/admin/students/{regdno}                → Full student profile
 * GET  /api/admin/students/{regdno}/applications   → Student's full application history
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentController {

    private final AdminStudentService adminStudentService;

    // ── Dashboard Stats ───────────────────────────────────────────────────────

    /**
     * GET /api/admin/stats
     * Returns portal-wide statistics for the admin dashboard home screen.
     *
     * Response:
     * {
     *   "totalStudents": 450,
     *   "totalDrives": 12,
     *   "openDrives": 4,
     *   "totalApplications": 1230,
     *   "placedStudents": 85,
     *   "shortlistedStudents": 230
     * }
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminStatsDTO>> getAdminStats() {
        AdminStatsDTO stats = adminStudentService.getAdminStats();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    // ── Student List ──────────────────────────────────────────────────────────

    /**
     * GET /api/admin/students
     * Returns all students with their application counts.
     * Each student shows totalApplications and placedCount.
     */
    @GetMapping("/students")
    public ResponseEntity<ApiResponse<Page<StudentSummaryDTO>>> getAllStudents(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
 
        Page<StudentSummaryDTO> result = adminStudentService.getAllStudents(page, size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * GET /api/admin/students/search?q=john
     * Searches students by name, regdno, branch code, or email.
     * Case-insensitive partial match.
     *
     * Example: /api/admin/students/search?q=CSE
     *   → returns all students with branchCode containing "CSE"
     *
     * Example: /api/admin/students/search?q=0601
     *   → returns students whose regdno starts with "0601"
     */
    @GetMapping("/students/search")
    public ResponseEntity<ApiResponse<List<StudentSummaryDTO>>> searchStudents(
            @RequestParam(name = "q", defaultValue = "") String query) {
        List<StudentSummaryDTO> students = adminStudentService.searchStudents(query);
        return ResponseEntity.ok(ApiResponse.ok(students));
    }

    // ── Student Detail ────────────────────────────────────────────────────────

    /**
     * GET /api/admin/students/{regdno}
     * Returns the full student profile (same as StudentDTO used in student module).
     * All fields from the STUDENT table.
     */
    @GetMapping("/students/{regdno}")
    public ResponseEntity<ApiResponse<StudentDTO>> getStudentProfile(
            @PathVariable String regdno) {
        StudentDTO student = adminStudentService.getStudentProfile(regdno);
        return ResponseEntity.ok(ApiResponse.ok(student));
    }

    /**
     * GET /api/admin/students/{regdno}/applications
     * Returns the complete application history for a student.
     * Includes drive details and current status for each application.
     */
    @GetMapping("/students/{regdno}/applications")
    public ResponseEntity<ApiResponse<List<AdminApplicationResponse>>> getStudentApplicationHistory(
            @PathVariable String regdno) {
        List<AdminApplicationResponse> applications =
                adminStudentService.getStudentApplicationHistory(regdno);
        return ResponseEntity.ok(ApiResponse.ok(applications));
    }
}