package com.trident.placement.controller;

import com.trident.placement.dto.ApiResponse;
import com.trident.placement.dto.UserRoleResponse;
import com.trident.placement.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles Azure AD authentication and role resolution.
 *
 * Called by the Next.js frontend immediately after Azure login:
 *   lib/auth/options.ts → fetchUserData(account.access_token)
 *   → GET /api/get-user-role
 *   → Authorization: Bearer <Azure_Access_Token>
 *
 * Request flow:
 *  1. AzureJwtAuthFilter validates token → sets SecurityContext with email + role authority
 *  2. Spring Security checks this endpoint is authenticated
 *  3. This controller extracts email from Authentication principal
 *  4. AuthService does the DB lookup and returns role + routes
 *  5. NextAuth stores role + menuBlade in the JWT session
 *  6. Frontend middleware uses allowedRoutes for route protection
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * GET /api/get-user-role
     *
     * Returns the role and allowed frontend routes for the authenticated user.
     *
     * Response (matches exact shape expected by NextAuth fetchUserData()):
     * {
     *   "role": "STUDENT",
     *   "redirectUrl": "/dashboard",
     *   "allowedRoutes": ["/dashboard", "/drives", "/applications", "/notifications", "/profile"]
     * }
     *
     * Note: We return UserRoleResponse directly (not wrapped in ApiResponse)
     * because the frontend's fetchUserData() reads userData.role, userData.redirectUrl,
     * userData.allowedRoutes directly — not userData.data.role.
     */
    @GetMapping("/get-user-role")
    public ResponseEntity<UserRoleResponse> getUserRole(Authentication authentication) {

        // Principal is the email string set by AzureJwtAuthFilter
        String email = (String) authentication.getPrincipal();
        log.debug("GET /api/get-user-role called for: {}", email);

        UserRoleResponse response = authService.getUserRole(email);
        return ResponseEntity.ok(response);
    }
}