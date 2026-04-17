package com.trident.placement.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One row per branch per drive.
 *
 * When an admin creates a drive with e.g. eligibleBranches=["CSE","ETC"],
 * two rows are inserted here:
 *   (drive_id=1, branch_code="CSE", course="B.TECH", passout_year=2026)
 *   (drive_id=1, branch_code="ETC", course="B.TECH", passout_year=2026)
 *
 * The eligibility assignment job reads these rows to know which students
 * (by branch + course + passout year + CGPA) to insert into ELIGIBLE_DRIVES.
 *
 * The table name is DRIVE_ELIGIBILITY; Primary key uses SEQ_DRIVE_ELIG.
 */
@Entity
@Table(name = "DRIVE_ELIGIBILITY")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "drive_elig_seq")
    @SequenceGenerator(name = "drive_elig_seq", sequenceName = "SEQ_DRIVE_ELIG", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    /**
     * The drive this eligibility row belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DRIVE_ID", nullable = false)
    private Drive drive;

    /**
     * Branch code eligible for this drive (e.g. "CSE", "ETC", "MECH").
     */
    @Column(name = "BRANCH_CODE", length = 20, nullable = false)
    private String branchCode;

    /**
     * Course eligible for this drive row (e.g. "B.TECH", "MCA").
     * If null, inherits from drive.eligibleCourse.
     */
    @Column(name = "COURSE", length = 50)
    private String course;

    /**
     * Passout year eligible for this drive row (e.g. 2026).
     * If null, inherits from drive.passoutYear.
     */
    @Column(name = "PASSOUT_YEAR")
    private Long passoutYear;
}
