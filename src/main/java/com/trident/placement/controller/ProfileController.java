package com.trident.placement.controller;

import com.trident.placement.dto.ApiResponse;
import com.trident.placement.dto.StudentDTO;
import com.trident.placement.service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final StudentService studentService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<StudentDTO>> getProfile(Authentication authentication) {

        log.info("Profile API called");

        String email = authentication.getName();
        log.info("Fetching profile for user: {}", email);

        StudentDTO student = studentService.getStudentByEmail(email);

        log.info("Profile fetched successfully for user: {}", email);

        return ResponseEntity.ok(ApiResponse.ok(student));
    }
}