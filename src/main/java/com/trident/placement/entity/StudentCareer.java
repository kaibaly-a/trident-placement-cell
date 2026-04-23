package com.trident.placement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Maps the STUDENT_CAREER table.
 *
 * Stores a student's academic career marks:
 *   - 10th percentage + year
 *   - 12th percentage + year
 *   - Diploma percentage + year (0 if not applicable)
 *   - Graduation percentage + year (0 if not applicable)
 *
 * IMPORTANT: All percentage fields use BigDecimal, NOT Double.
 * Oracle NUMBER columns do not map reliably to Java Double via Hibernate —
 * the driver silently returns null for NUMBER columns mapped to Double.
 * BigDecimal maps to Oracle NUMBER correctly every time.
 *
 * Used by the drive eligibility filter to check if a student
 * meets the career marks criteria set by admin when posting a drive.
 */
@Entity
@Table(name = "STUDENT_CAREER")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentCareer {

    @Id
    @Column(name = "REGDNO", length = 255)
    private String regdno;

    @Column(name = "TENTHPERCENTAGE", precision = 5, scale = 2)
    private BigDecimal tenthPercentage;

    @Column(name = "TENTHYOP")
    private Long tenthYop;

    @Column(name = "TWELVTHPERCENTAGE", precision = 5, scale = 2)
    private BigDecimal twelvthPercentage;

    @Column(name = "TWELVTHYOP")
    private Long twelvthYop;

    @Column(name = "DIPLOMAPERCENTAGE", precision = 5, scale = 2)
    private BigDecimal diplomaPercentage;

    @Column(name = "DIPLOMAYOP")
    private Long diplomaYop;

    @Column(name = "GRADUATIONPERCENTAGE", precision = 5, scale = 2)
    private BigDecimal graduationPercentage;

    @Column(name = "GRADUATIONYOP")
    private Long graduationYop;
}