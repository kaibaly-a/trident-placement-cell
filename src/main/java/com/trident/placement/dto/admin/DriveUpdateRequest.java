package com.trident.placement.dto.admin;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for PUT /api/admin/drives/{id}
 * All fields are optional — only non-null fields are updated (PATCH semantics).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveUpdateRequest {

    @Size(max = 200, message = "Company name must not exceed 200 characters")
    private String companyName;

    @Size(max = 150, message = "Job role must not exceed 150 characters")
    private String role;

    // ON_CAMPUS, OFF_CAMPUS, POOL — null means no change
    private String driveType;

    @DecimalMin(value = "0.0", inclusive = false, message = "LPA package must be greater than 0")
    @Digits(integer = 3, fraction = 2, message = "LPA format: up to 3 digits and 2 decimal places")
    private BigDecimal lpaPackage;

    @DecimalMin(value = "0.0", inclusive = false, message = "Minimum CGPA must be greater than 0")
    @DecimalMax(value = "10.0", message = "Minimum CGPA cannot exceed 10.0")
    @Digits(integer = 2, fraction = 2, message = "CGPA format: up to 2 digits and 2 decimal places")
    private BigDecimal minimumCgpa;

    private LocalDate lastDate;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @Size(max = 50)
    private String eligibleCourse;

    private Long passoutYear;

    private java.util.List<String> branches;
}