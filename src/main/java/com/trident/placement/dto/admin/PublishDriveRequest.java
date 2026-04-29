package com.trident.placement.dto.admin;

import lombok.Data;
import java.util.List;

/**
 * Request body for PATCH /api/admin/drives/{id}/publish
 *
 * Admin sends the list of regdnos they have selected
 * in the "Review & Publish" step. Only these students
 * will have the drive visible in their dashboard.
 *
 * If selectedRegdnos is null or empty, ALL eligible
 * students are included (fallback safety net).
 */
@Data
public class PublishDriveRequest {
    private List<String> selectedRegdnos;
}