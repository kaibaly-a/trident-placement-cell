package com.trident.placement.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One row per selection step for a DriveJD.
 * Ordered by STEP_ORDER column (managed by @OrderColumn on parent).
 */
@Entity
@Table(name = "DRIVE_JD_STEPS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriveJDSelectionStep {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "drive_jd_step_seq")
    @SequenceGenerator(
            name           = "drive_jd_step_seq",
            sequenceName   = "SEQ_DRIVE_JD_STEP",
            allocationSize = 1
    )
    @Column(name = "ID")
    private Long id;

    // ── Parent ────────────────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DRIVE_JD_ID", nullable = false,
                foreignKey = @ForeignKey(name = "FK_JD_STEP_DRIVE_JD"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private DriveJD driveJD;

    // ── Step Data ─────────────────────────────────────────────────────────────
    @Column(name = "DESCRIPTION", nullable = false, length = 255)
    private String description;

    @Column(name = "ELIMINATION_ROUND", nullable = false)
    private boolean eliminationRound;

    // Managed by @OrderColumn("STEP_ORDER") on DriveJD.selectionSteps
    // No need to declare the column here — JPA handles it.
}