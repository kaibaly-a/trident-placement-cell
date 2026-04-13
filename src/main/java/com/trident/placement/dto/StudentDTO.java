package com.trident.placement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDTO {
    private String regdno;
    private String name;
    private String gender;
    private String dob;
    private String course;
    private String branchCode;
    private String admissionYear;
    private Long degreeYop;
    private String phno;
    private String email;
    private String studentType;
    private String hostelier;
    private String transportAvailed;
    private String status;
    private String batchId;
    private Long currentYear;
    private Long aadharno;
    private String indortrng;
    private String plpoolm;
    private String cfpaymode;
    private String religion;
    private String msUserPrincipalName;
    private String collegeName;
}

