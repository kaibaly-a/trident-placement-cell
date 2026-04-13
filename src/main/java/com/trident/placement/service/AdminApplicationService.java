package com.trident.placement.service;

import com.trident.placement.dto.admin.AdminApplicationResponse;
import com.trident.placement.dto.admin.ApplicationStatusUpdateRequest;

import java.util.List;

public interface AdminApplicationService {
    List<AdminApplicationResponse> getAllApplications();
    List<AdminApplicationResponse> getApplicationsByDrive(Long driveId);
    List<AdminApplicationResponse> getApplicationsByStatus(String status);
    List<AdminApplicationResponse> getApplicationsByDriveAndStatus(Long driveId, String status);
    AdminApplicationResponse updateApplicationStatus(Long applicationId,
                                                     ApplicationStatusUpdateRequest request);
    byte[] exportApplicationsAsCsv(Long driveId);
}