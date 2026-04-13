package com.trident.placement.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trident.placement.dto.ApiResponse;
import com.trident.placement.dto.ApplicationDTO;
import com.trident.placement.service.ApplicationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping("/{regdno}")
    public ResponseEntity<ApiResponse<List<ApplicationDTO>>> getApplications(
            @PathVariable String regdno) {

        List<ApplicationDTO> apps = applicationService.getStudentApplications(regdno);
        return ResponseEntity.ok(ApiResponse.ok(apps));
    }

    @GetMapping("/{regdno}/status/{status}")
    public ResponseEntity<ApiResponse<List<ApplicationDTO>>> getApplicationsByStatus(
            @PathVariable String regdno,
            @PathVariable String status) {

        try {
            List<ApplicationDTO> apps =
                    applicationService.getStudentApplicationsByStatus(regdno, status);

            return ResponseEntity.ok(ApiResponse.ok(apps));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{regdno}/apply/{driveId}")
    public ResponseEntity<ApiResponse<ApplicationDTO>> apply(
            @PathVariable String regdno,
            @PathVariable Long driveId) {

        try {
            ApplicationDTO app =
                    applicationService.applyToDrive(regdno, driveId);

            return ResponseEntity.ok(
                    ApiResponse.ok("Application submitted successfully", app)
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}