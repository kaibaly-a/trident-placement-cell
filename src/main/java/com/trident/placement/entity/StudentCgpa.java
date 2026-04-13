package com.trident.placement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores the calculated CGPA for each student.
 *
 * Acts as a persistent cache — when a student's CGPA is fetched
 * from BPUT for the first time, it is stored here. Subsequent
 * eligibility checks use this value instead of calling BPUT again.
 *
 * Updated when:
 *  - First time a drive is posted and student has no CGPA record
 *  - Admin manually triggers a CGPA refresh (future feature)
 */
@Entity
@Table(name = "student_cgpa")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentCgpa {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "student_cgpa_seq")
    @SequenceGenerator(
            name = "student_cgpa_seq",
            sequenceName = "SEQ_STUDENT_CGPA",
            allocationSize = 1
    )
    private Long id;

    /**
     * Maps to STUDENT.REGDNO — the student's registration number.
     * Unique — one CGPA record per student.
     */
    @Column(name = "regdno", nullable = false, unique = true, length = 255)
    private String regdno;

    /**
     * Calculated CGPA from BPUT results.
     * Precision: 4 digits total, 2 decimal places (e.g., 8.75, 10.00)
     */
    @Column(name = "cgpa", nullable = false, precision = 4, scale = 2)
    private BigDecimal cgpa;

    /**
     * When the CGPA was first fetched from BPUT.
     */
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private LocalDateTime fetchedAt;

    /**
     * When the CGPA was last updated (for future refresh support).
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        fetchedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}