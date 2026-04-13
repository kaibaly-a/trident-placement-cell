package com.trident.placement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveDTO {
    private Long id;
    private String companyName;
    private String role;
    private String type;        // "On-Campus", "Virtual", "Off-Campus"
    private BigDecimal lpaPackage;
    private BigDecimal minimumCgpa;
    private String lastDate;    // formatted as DD-MM-YY
    private String description;
    private String status;      // "OPEN" or "CLOSED"
    private java.util.List<String> eligibleBranches;
}

