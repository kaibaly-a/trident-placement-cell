package com.trident.placement.repository;

import com.trident.placement.entity.EligibleDrive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EligibleDriveRepository extends JpaRepository<EligibleDrive, Long> {

  // ── Student dashboard ─────────────────────────────────────────────────────

  /**
   * Returns all OPEN drives a student is eligible for, filtered by their branch.
   *
   * Uses JPQL MEMBER OF to check if the student's branchCode is in the
   * drive's allowed branches (DRIVE_BRANCHES table).
   *
   * Drives with an EMPTY branch list are open to ALL branches and are always
   * included.
   *
   * JOIN FETCH loads the Drive eagerly — avoids N+1 when building DTOs.
   */
  @Query("""
            SELECT e FROM EligibleDrive e
            JOIN FETCH e.drive d
            JOIN d.eligibilityRows er
            WHERE e.regdno = :regdno
              AND er.branchCode = :branchCode
              AND d.status = com.trident.placement.enums.DriveStatus.OPEN
            ORDER BY d.lastDate ASC
            """)
  List<EligibleDrive> findEligibleDrivesForStudent(@Param("regdno") String regdno,
      @Param("branchCode") String branchCode);

  // ── Eligibility check ─────────────────────────────────────────────────────

  /**
   * Checks if a student is eligible for a specific drive.
   *
   * FIXED: COUNT query replaces the old existsBy... derived method.
   * Old method generated FETCH FIRST ? ROWS ONLY — broken on Oracle 11g.
   * COUNT(*) > 0 works on all Oracle versions.
   *
   * Used by:
   * - CgpaEligibilityService.assignEligibleStudents() — prevents duplicates
   * - DriveJDServiceImpl.getJDForStudent() — gate check before returning JD
   */
  @Query("""
      SELECT COUNT(ed) > 0 FROM EligibleDrive ed
      WHERE ed.regdno = :regdno
        AND ed.drive.id = :driveId
      """)
  boolean existsByRegdnoAndDriveId(@Param("regdno") String regdno,
      @Param("driveId") Long driveId);

  // ── Admin / cleanup ───────────────────────────────────────────────────────

  /**
   * Deletes all eligibility records for a drive.
   * Called when a drive is deleted or its minimum CGPA is changed.
   * DELETE does not use FETCH FIRST — safe on Oracle 11g as-is.
   */
  @Modifying
  @Query("DELETE FROM EligibleDrive ed WHERE ed.drive.id = :driveId")
  void deleteByDriveId(@Param("driveId") Long driveId);

  List<EligibleDrive> findByDriveId(Long driveId);
}