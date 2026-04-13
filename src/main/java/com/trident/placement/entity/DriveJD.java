package com.trident.placement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the full Job Description for a Drive.
 * One Drive → One DriveJD (created separately after drive creation).
 * Selection steps are stored as child rows in DRIVE_JD_STEPS.
 */
@Entity
@Table(name = "DRIVE_JD")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveJD {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "drive_jd_seq")
    @SequenceGenerator(
            name        = "drive_jd_seq",
            sequenceName = "SEQ_DRIVE_JD",
            allocationSize = 1
    )
    @Column(name = "ID")
    private Long id;

    // ── Owning side of the relationship ──────────────────────────────────────
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DRIVE_ID", nullable = false, unique = true,
                foreignKey = @ForeignKey(name = "FK_DRIVE_JD_DRIVE"))
    private Drive drive;

    // ── Role & Job Info ───────────────────────────────────────────────────────
    @Column(name = "JOB_LOCATION", length = 255)
    private String jobLocation;

    @Column(name = "EMPLOYMENT_TYPE", length = 50)
    private String employmentType;       // Full Time, Internship, Part Time, Contract

    @Column(name = "WORK_MODE", length = 50)
    private String workMode;             // On-Site, Remote, Hybrid

    @Column(name = "VACANCIES", length = 100)
    private String vacancies;

    @Column(name = "SERVICE_AGREEMENT", length = 255)
    private String serviceAgreement;

    @Column(name = "JOINING_INFO", length = 255)
    private String joining;

    // ── Eligibility ───────────────────────────────────────────────────────────
    @Column(name = "CGPA_CUTOFF_DISPLAY", length = 20)
    private String cgpaCutoff;           // display string e.g. "6.5" or "No Cutoff"

    @Column(name = "BACKLOGS_ALLOWED")
    private Boolean backlogsAllowed = false;

    // Stored as pipe-separated: "CSE|ETC|EEE"
    @Column(name = "ALLOWED_BRANCHES", length = 500)
    private String allowedBranches;

    // Stored as pipe-separated: "B.Tech|MCA"
    @Column(name = "ALLOWED_COURSES", length = 255)
    private String allowedCourses;

    @Column(name = "BATCH", length = 100)
    private String batch;

    // ── Company & Role Content ────────────────────────────────────────────────
    @Column(name = "ABOUT_COMPANY", length = 2000)
    private String aboutCompany;

    @Column(name = "WEBSITE", length = 255)
    private String website;

    @Column(name = "HEADQUARTERS", length = 255)
    private String headquarters;

    @Column(name = "ROLE_OVERVIEW", length = 2000)
    private String roleOverview;

    // Stored as pipe-separated: "Java|Spring Boot|SQL"
    @Column(name = "REQUIRED_SKILLS", length = 1000)
    private String requiredSkills;

    // Stored as pipe-separated
    @Column(name = "KEY_RESPONSIBILITIES", length = 2000)
    private String keyResponsibilities;

    // Stored as pipe-separated
    @Column(name = "WHY_JOIN", length = 1000)
    private String whyJoin;

    // ── Selection Process (child rows) ────────────────────────────────────────
    @OneToMany(
            mappedBy      = "driveJD",
            cascade       = CascadeType.ALL,
            orphanRemoval = true,
            fetch         = FetchType.LAZY
    )
    @OrderColumn(name = "STEP_ORDER")
    @Builder.Default
    private List<DriveJDSelectionStep> selectionSteps = new ArrayList<>();
}