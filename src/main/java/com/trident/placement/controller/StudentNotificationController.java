package com.trident.placement.controller;

import com.trident.placement.dto.student.StudentNotificationResponse;
import com.trident.placement.entity.Student;
import com.trident.placement.repository.StudentRepository;
import com.trident.placement.service.ShortlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/notifications")
@RequiredArgsConstructor
@Slf4j
public class StudentNotificationController {

    private final ShortlistService shortlistService;
    private final StudentRepository studentRepository;

    /**
     * GET /api/student/notifications
     *
     * Returns a paginated list of shortlist notifications for the currently
     * authenticated student.
     */
    @GetMapping
    public ResponseEntity<StudentNotificationResponse> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            throw new RuntimeException("Unauthorized: unauthenticated request");
        }

        final String finalEmail = email;
        log.debug("Fetching notifications for student: {}", finalEmail);

        Student student = studentRepository.findByMsUserPrincipalName(finalEmail)
                .or(() -> studentRepository.findByEmail(finalEmail))
                .orElseThrow(() -> new RuntimeException("Authenticated student not found in DB"));

        StudentNotificationResponse response = shortlistService.getStudentNotifications(student.getRegdno(), page, pageSize);
        return ResponseEntity.ok(response);
    }
}
