package com.trident.placement.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "STUDENT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @Column(name = "REGDNO", length = 255)
    private String regdno;

    @Column(name = "NAME", length = 255)
    private String name;

    @Column(name = "GENDER", length = 255)
    private String gender;

    @Column(name = "DOB", length = 255)
    private String dob;

    @Column(name = "COURSE", length = 255)
    private String course;

    @Column(name = "BRANCH_CODE", length = 255)
    private String branchCode;

    @Column(name = "ADMISSIONYEAR", length = 20)
    private String admissionYear;

    @Column(name = "DEGREE_YOP")
    private Long degreeYop;

    @Column(name = "PHNO", length = 255)
    private String phno;

    @Column(name = "EMAIL", length = 255)
    private String email;

    @Column(name = "STUDENTTYPE", length = 50)
    private String studentType;

    @Column(name = "HOSTELIER", length = 50)
    private String hostelier;

    @Column(name = "TRANSPORTAVAILED", length = 50)
    private String transportAvailed;

    @Column(name = "STATUS", length = 255)
    private String status;

    @Column(name = "BATCHID", length = 255)
    private String batchId;

    @Column(name = "CURRENTYEAR")
    private Long currentYear;

    @Column(name = "AADHAARNO")
    private Long aadhaarno;

    @Column(name = "INDORTRNG", length = 20)
    private String indortrng;

    @Column(name = "PLPOOLM", length = 20)
    private String plpoolm;

    @Column(name = "CFPAYMODE", length = 20)
    private String cfpaymode;

    @Column(name = "RELIGION", length = 20)
    private String religion;

    @Column(name = "MSUSERPRINCIPALNAME", length = 100)
    private String msUserPrincipalName;

    @Column(name = "COLLEGENAME", length = 10)
    private String collegeName;
}
