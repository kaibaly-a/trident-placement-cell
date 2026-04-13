package com.trident.placement.service;

import com.trident.placement.dto.UserRoleResponse;
import com.trident.placement.entity.Admin;
import com.trident.placement.entity.Student;
import com.trident.placement.enums.Role;
import com.trident.placement.repository.AdminRepository;
import com.trident.placement.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the role and allowed frontend routes for an authenticated Azure AD user.
 *
 * Called by AuthController after AzureJwtAuthFilter has already:
 *  - Validated the token
 *  - Extracted the email
 *  - Set the SecurityContext
 *
 * The frontend (NextAuth jwt() callback) uses this response to:
 *  - Store token.role
 *  - Store token.menuBlade.redirectUrl
 *  - Store token.menuBlade.allowedRoutes
 * Which the middleware then uses for route protection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AdminRepository adminRepository;
    private final StudentRepository studentRepository;

    // ── Route maps — must match frontend middleware.ts matcher config ────────

    private static final Map<String, String> REDIRECT_URLS = Map.of(
            "STUDENT", "/dashboard",
            "ADMIN",   "/admin",
            "TPO",     "/tpo"
    );

    private static final Map<String, List<String>> ALLOWED_ROUTES = Map.of(
            "STUDENT", List.of(
                    "/dashboard",
                    "/drives",
                    "/applications",
                    "/notifications",
                    "/profile"
            ),
            "ADMIN", List.of(
                    "/admin",
                    "/dashboard",
                    "/drives",
                    "/applications"
            ),
            "TPO", List.of(
                    "/tpo",
                    "/dashboard",
                    "/drives",
                    "/applications"
            )
    );

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Resolves role + routing info for the given email.
     *
     * The email comes from the Azure token "preferred_username" claim,
     * already extracted by AzureJwtAuthFilter. By the time this method runs,
     * the token is guaranteed valid — we just do the DB lookup.
     *
     * Resolution order:
     *  1. admins table    → ADMIN or TPO
     *  2. students table  → STUDENT (by msUserPrincipalName, fallback to email)
     *
     * @param email  lowercase email extracted from Azure token
     * @return UserRoleResponse with role, redirectUrl, allowedRoutes
     * @throws RuntimeException if user not found in either table
     */
    @Transactional(readOnly = true)
    public UserRoleResponse getUserRole(String email) {

        // Check admins table first
        Optional<Admin> adminOpt = adminRepository.findByEmailIgnoreCase(email);
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();

            if (!admin.isActive()) {
                throw new RuntimeException("Account is disabled. Contact the system administrator.");
            }

            String roleName = admin.getRole().name(); // "ADMIN" or "TPO"
            log.info("Role resolved: {} for email: {}", roleName, email);
            return buildResponse(roleName);
        }

        // Check students table via MSUSERPRINCIPALNAME (Azure UPN)
        Optional<Student> studentOpt = studentRepository.findByMsUserPrincipalName(email);
        if (studentOpt.isPresent()) {
            log.info("Role resolved: STUDENT for email: {} (via msUserPrincipalName)", email);
            return buildResponse("STUDENT");
        }

        // Fallback: check by student email column
        Optional<Student> studentByEmailOpt = studentRepository.findByEmail(email);
        if (studentByEmailOpt.isPresent()) {
            log.info("Role resolved: STUDENT for email: {} (via email column)", email);
            return buildResponse("STUDENT");
        }

        // User authenticated with Azure but not registered in the portal
        log.warn("Authenticated Azure user not found in portal: {}", email);
        throw new RuntimeException(
                "User not registered in the placement portal. Contact your administrator."
        );
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private UserRoleResponse buildResponse(String role) {
        return UserRoleResponse.builder()
                .role(role)
                .redirectUrl(REDIRECT_URLS.get(role))
                .allowedRoutes(ALLOWED_ROUTES.get(role))
                .build();
    }
}