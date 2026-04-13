package com.trident.placement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDTO {
    private Long id;
    private String regdno;
    private Long driveId;
    private String companyName;
    private String role;
    private String appliedDate; // formatted as DD-MM-YY
    private String status;      // APPLIED, SHORTLISTED, APPROVED, REJECTED
}
