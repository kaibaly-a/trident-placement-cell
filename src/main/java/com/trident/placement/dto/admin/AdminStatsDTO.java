package com.trident.placement.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard statistics shown on the Admin home screen.
 * GET /api/admin/stats
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsDTO {
    private long totalStudents;
    private long totalDrives;
    private long openDrives;
    private long totalApplications;
    private long placedStudents;       // applications with status = APPROVED
    private long shortlistedStudents;  // applications with status = SHORTLISTED
}