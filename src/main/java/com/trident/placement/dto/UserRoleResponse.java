package com.trident.placement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Exact response shape consumed by the Next.js frontend in:
 *   lib/auth/options.ts → fetchUserData() → jwt() callback
 *
 * Frontend stores: token.role, token.menuBlade.redirectUrl, token.menuBlade.allowedRoutes
 *
 * Example responses:
 *
 * STUDENT:
 * {
 *   "role": "STUDENT",
 *   "redirectUrl": "/student/dashboard",
 *   "allowedRoutes": ["/dashboard", "/drives", "/applications", "/notifications", "/profile"]
 * }
 *
 * ADMIN:
 * {
 *   "role": "ADMIN",
 *   "redirectUrl": "/admin",
 *   "allowedRoutes": ["/admin", "/dashboard", "/drives", "/applications"]
 * }
 *
 * TPO:
 * {
 *   "role": "TPO",
 *   "redirectUrl": "/tpo",
 *   "allowedRoutes": ["/tpo", "/dashboard", "/drives", "/applications"]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleResponse {
    private String role;
    private String redirectUrl;
    private List<String> allowedRoutes;
}