package com.trident.placement.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Validates Azure AD access tokens using Spring's JwtDecoder.
 *
 * Spring auto-configures JwtDecoder from application.properties:
 *   spring.security.oauth2.resourceserver.jwt.jwk-set-uri
 *
 * The decoder:
 *   1. Fetches Azure's public signing keys from the JWKS endpoint (cached)
 *   2. Verifies the token's RS256 signature
 *   3. Checks expiry (exp) and not-before (nbf) claims automatically
 *
 * You do NOT need any additional JWT library — nimbus-jose-jwt is already
 * pulled in transitively by spring-boot-starter-oauth2-resource-server.
 */
@Service
@Slf4j
public class AzureTokenValidator {

    private final JwtDecoder jwtDecoder;

    @Value("${azure.ad.client-id}")
    private String clientId;

    // Spring injects the auto-configured JwtDecoder bean
    public AzureTokenValidator(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    /**
     * Validates token signature + expiry and returns the decoded Jwt.
     *
     * @param rawToken  the raw token string from "Authorization: Bearer <token>"
     * @return decoded Jwt if valid and not expired, empty if invalid
     */
    public Optional<Jwt> validateAndDecode(String rawToken) {
        try {
            Jwt jwt = jwtDecoder.decode(rawToken);
            log.debug("Token validated successfully. Subject: {}", jwt.getSubject());
            return Optional.of(jwt);
        } catch (JwtException ex) {
            // Covers: bad signature, expired, malformed, wrong issuer
            log.warn("Azure JWT validation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts the user's email/UPN from the Azure AD token.
     *
     * Azure AD claim resolution order:
     *  1. preferred_username  — the user's UPN (e.g., "john@trident.ac.in")
     *                           This is what's stored in Student.msUserPrincipalName
     *  2. email               — only present if configured as optional claim in Azure App Registration
     *  3. upn                 — legacy fallback, same value as preferred_username
     *
     * @param jwt decoded Jwt from validateAndDecode()
     * @return email string (lowercased, trimmed), or empty if no claim found
     */
    public Optional<String> extractEmail(Jwt jwt) {

        // Priority 1: preferred_username (standard for Azure AD — matches MSUSERPRINCIPALNAME)
        String email = getStringClaim(jwt, "preferred_username");

        // Priority 2: email optional claim
        if (isBlank(email)) {
            email = getStringClaim(jwt, "email");
            if (!isBlank(email)) {
                log.debug("Used 'email' claim fallback for subject: {}", jwt.getSubject());
            }
        }

        // Priority 3: upn legacy claim
        if (isBlank(email)) {
            email = getStringClaim(jwt, "upn");
            if (!isBlank(email)) {
                log.debug("Used 'upn' claim fallback for subject: {}", jwt.getSubject());
            }
        }

        if (isBlank(email)) {
            log.warn("No email claim found in token. Subject: {}. Claims: {}",
                    jwt.getSubject(), jwt.getClaims().keySet());
            return Optional.empty();
        }

        return Optional.of(email.toLowerCase().trim());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String getStringClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaims().get(claimName);
        return value != null ? value.toString() : null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}