package com.trident.placement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Records which student is eligible for which drive.
 *
 * Populated when admin creates a drive — the system immediately
 * checks all students' CGPAs and inserts a row here for each
 * student whose CGPA meets the drive's minimum requirement.
 *
 * When a student opens their dashboard, we query this table
 * by regdno — instant response, no BPUT call needed.
 */
@Entity
@Table(
    name = "eligible_drives",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_eligible_drives_regdno_drive",
            columnNames = {"regdno", "drive_id"}
        )
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EligibleDrive {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eligible_drives_seq")
    @SequenceGenerator(
            name = "eligible_drives_seq",
            sequenceName = "SEQ_ELIGIBLE_DRIVES",
            allocationSize = 1
    )
    private Long id;

    /**
     * Student registration number — maps to STUDENT.REGDNO.
     */
    @Column(name = "regdno", nullable = false, length = 255)
    private String regdno;

    /**
     * The drive this student is eligible for.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drive_id", nullable = false)
    private Drive drive;

    /**
     * When this eligibility record was created.
     */
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    protected void onCreate() {
        assignedAt = LocalDateTime.now();
    }
}