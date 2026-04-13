package com.trident.placement.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trident.placement.dto.ApiResponse;
import com.trident.placement.dto.DriveDTO;
import com.trident.placement.service.DriveService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/drives")
@RequiredArgsConstructor
public class DriveController {

    private final DriveService driveService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DriveDTO>>> getAllDrives() {

        List<DriveDTO> drives = driveService.getAllDrives();

        return ResponseEntity.ok(ApiResponse.ok(drives));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DriveDTO>> getDriveById(@PathVariable Long id) {

        DriveDTO drive = driveService.getDriveById(id);

        return ResponseEntity.ok(ApiResponse.ok(drive));
    }

    @GetMapping("/eligible/{regdno}")
    public ResponseEntity<ApiResponse<List<DriveDTO>>> getEligibleDrives(
            @PathVariable String regdno) {

        List<DriveDTO> drives = driveService.getEligibleDrives(regdno);

        return ResponseEntity.ok(ApiResponse.ok(drives));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<DriveDTO>>> getDrivesByType(
            @PathVariable String type) {

        List<DriveDTO> drives = driveService.getDriveByType(type);

        return ResponseEntity.ok(ApiResponse.ok(drives));
    }

    @GetMapping("/open")
    public ResponseEntity<ApiResponse<List<DriveDTO>>> getOpenDrives() {

        List<DriveDTO> drives = driveService.getOpenDrives();

        return ResponseEntity.ok(ApiResponse.ok(drives));
    }
}
