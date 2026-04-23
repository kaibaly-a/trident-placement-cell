package com.trident.placement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.trident.placement.enums.DriveStatus;
import com.trident.placement.enums.DriveType;

@Entity
@Table(name = "DRIVES")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Drive {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "drive_seq")
    @SequenceGenerator(name = "drive_seq", sequenceName = "SEQ_DRIVE", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "COMPANY_NAME", nullable = false, length = 200)
    private String companyName;

    @Column(name = "JOB_ROLE", nullable = false, length = 150)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(name = "DRIVE_TYPE", nullable = false, length = 20)
    private DriveType driveType;

    @Column(name = "LPA_PACKAGE", precision = 5, scale = 2)
    private BigDecimal lpaPackage;

    @Column(name = "MINIMUM_CGPA", nullable = false, precision = 4, scale = 2)
    private BigDecimal minimumCgpa;

    @Column(name = "LAST_DATE", nullable = false)
    private LocalDate lastDate;

    @Column(name = "DESCRIPTION", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private DriveStatus status = DriveStatus.OPEN;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * One row per eligible branch for this drive.
     * Populated at drive creation; read by CgpaEligibilityService.
     */
    @OneToMany(mappedBy = "drive", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DriveEligibility> eligibilityRows = new ArrayList<>();

    /** Drive-level default course (e.g. "B.TECH"). Each eligibility row may override. */
    @Column(name = "ELIGIBLE_COURSE", length = 50)
    private String eligibleCourse;

    /** Drive-level default passout year (e.g. 2026). Each eligibility row may override. */
    @Column(name = "PASSOUT_YEAR")
    private Long passoutYear;

    // ── Career Marks Eligibility Criteria ────────────────────────────────────
    // All nullable — null means "no minimum required" for that field.
    // Example: minTenthPercent = 60.0 means student must have >= 60% in 10th.

    @Column(name = "MIN_TENTH_PERCENT", precision = 5, scale = 2)
private BigDecimal minTenthPercent;

@Column(name = "MIN_TWELFTH_PERCENT", precision = 5, scale = 2)
private BigDecimal minTwelfthPercent;

@Column(name = "MIN_DIPLOMA_PERCENT", precision = 5, scale = 2)
private BigDecimal minDiplomaPercent;

@Column(name = "MIN_GRADUATION_PERCENT", precision = 5, scale = 2)
private BigDecimal minGraduationPercent;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}