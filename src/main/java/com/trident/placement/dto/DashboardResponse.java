package com.trident.placement.dto;

import java.util.List;

public record DashboardResponse(
        StudentDTO student,
        DashboardStatsDTO stats,
        List<DriveDTO> eligibleDrives
) {}