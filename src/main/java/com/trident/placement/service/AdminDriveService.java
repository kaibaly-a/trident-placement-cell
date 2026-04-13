package com.trident.placement.service;

import com.trident.placement.dto.admin.AdminDriveResponse;
import com.trident.placement.dto.admin.DriveCreateRequest;
import com.trident.placement.dto.admin.DriveUpdateRequest;

import java.util.List;

public interface AdminDriveService {
    List<AdminDriveResponse> getAllDrivesWithCounts();
    AdminDriveResponse getDriveById(Long id);
    AdminDriveResponse createDrive(DriveCreateRequest request);
    AdminDriveResponse updateDrive(Long id, DriveUpdateRequest request);
    void deleteDrive(Long id);
    AdminDriveResponse toggleDriveStatus(Long id);
}