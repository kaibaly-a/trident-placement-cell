package com.trident.placement.security;

import com.trident.placement.entity.Admin;
import com.trident.placement.entity.Student;
import com.trident.placement.enums.Role;
import com.trident.placement.repository.AdminRepository;
import com.trident.placement.repository.StudentRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Intercepts every HTTP request and validates the Azure AD Bearer token.
 *
 * Authentication resolution order (same Azure AD tenant, different tables):
 *
 *  1. Extract "Authorization: Bearer <token>" header
 *  2. Validate token signature + expiry via AzureTokenValidator
 *  3. Extract email from "preferred_username" claim
 *  4. Check admins table first  → role = ADMIN or TPO
 *  5. Check students table next → role = STUDENT
 *      (uses MSUSERPRINCIPALNAME column which stores the Azure UPN)
 *  6. Set Spring SecurityContext with email as principal + ROLE_xxx authority
 *
 * If token is missing or invalid → filter passes through silently.
 * Spring Security then enforces authentication on protected endpoints.
 *
 * The authenticated email is available in controllers via:
 *   Authentication auth → (String) auth.getPrincipal()
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AzureJwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AzureTokenValidator tokenValidator;
    private final AdminRepository adminRepository;
    private final StudentRepository studentRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No Authorization header — let Spring Security handle it downstream
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String rawToken = authHeader.substring(BEARER_PREFIX.length()).trim();

        // Step 1: Validate and decode the Azure AD token
        Optional<Jwt> jwtOpt = tokenValidator.validateAndDecode(rawToken);
        if (jwtOpt.isEmpty()) {
            log.debug("Token validation failed for request: {}", request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        Jwt jwt = jwtOpt.get();

        // Step 2: Extract email from preferred_username / email / upn claim
        Optional<String> emailOpt = tokenValidator.extractEmail(jwt);
        if (emailOpt.isEmpty()) {
            log.warn("Could not extract email from valid token. URI: {}", request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        String email = emailOpt.get(); // already lowercased

        // Step 3: Resolve role — check admins table first, then students
        String roleAuthority = resolveRoleAuthority(email);

        if (roleAuthority == null) {
            // Valid Azure AD user but not registered in this portal
            log.debug("Azure user '{}' not found in portal (not in admins or students table)", email);
            chain.doFilter(request, response);
            return;
        }

        // Step 4: Build Spring Security authentication token
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,          // principal  → accessible as auth.getPrincipal()
                        null,           // credentials (not needed for JWT)
                        List.of(new SimpleGrantedAuthority(roleAuthority))
                );

        // Set in SecurityContext — this makes the user "authenticated" for this request
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated user: {} with authority: {}", email, roleAuthority);

        chain.doFilter(request, response);
    }

    /**
     * Resolves the Spring Security role string for a given email.
     *
     * Lookup priority:
     *  1. admins table (ADMIN / TPO)
     *  2. students table via MSUSERPRINCIPALNAME column
     *
     * Returns "ROLE_ADMIN", "ROLE_TPO", "ROLE_STUDENT", or null if not found.
     */
    private String resolveRoleAuthority(String email) {

        // Check admins table first
        Optional<Admin> adminOpt = adminRepository.findByEmailIgnoreCase(email);
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            if (!admin.isActive()) {
                log.warn("Inactive admin account attempted login: {}", email);
                return null;
            }
            return "ROLE_" + admin.getRole().name(); // ROLE_ADMIN or ROLE_TPO
        }

        // Check students table via msUserPrincipalName (Azure UPN stored during provisioning)
        Optional<Student> studentOpt = studentRepository.findByMsUserPrincipalName(email);
        if (studentOpt.isPresent()) {
            return "ROLE_STUDENT";
        }

        // Also try by email column as fallback
        Optional<Student> studentByEmailOpt = studentRepository.findByEmail(email);
        if (studentByEmailOpt.isPresent()) {
            return "ROLE_STUDENT";
        }

        return null; // Not found in either table
    }
}