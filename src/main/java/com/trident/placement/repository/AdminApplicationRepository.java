package com.trident.placement.repository;

import com.trident.placement.entity.Application;
import com.trident.placement.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Admin-specific application queries.
 * All queries JOIN fetch student + drive to avoid N+1 in admin views.
 */
@Repository
public interface AdminApplicationRepository extends JpaRepository<Application, Long> {

    /**
     * All applications across all drives — full admin view.
     * JOIN fetches student and drive in one query.
     */
    @Query("""
            SELECT a FROM Application a
            JOIN FETCH a.drive d
            ORDER BY a.appliedDate DESC, a.id DESC
            """)
    List<Application> findAllWithDrive();

    /**
     * All applications for a specific drive.
     */
    @Query("""
            SELECT a FROM Application a
            JOIN FETCH a.drive d
            WHERE a.drive.id = :driveId
            ORDER BY a.appliedDate DESC
            """)
    List<Application> findByDriveId(@Param("driveId") Long driveId);

    /**
     * All applications filtered by status.
     */
    @Query("""
            SELECT a FROM Application a
            JOIN FETCH a.drive d
            WHERE a.status = :status
            ORDER BY a.appliedDate DESC
            """)
    List<Application> findByStatus(@Param("status") ApplicationStatus status);

    /**
     * Applications for a specific drive filtered by status.
     */
    @Query("""
            SELECT a FROM Application a
            JOIN FETCH a.drive d
            WHERE a.drive.id = :driveId
              AND a.status = :status
            ORDER BY a.appliedDate DESC
            """)
    List<Application> findByDriveIdAndStatus(
            @Param("driveId") Long driveId,
            @Param("status") ApplicationStatus status);

    /**
     * All applications for a specific student — used in admin student detail view.
     */
    @Query("""
            SELECT a FROM Application a
            JOIN FETCH a.drive d
            WHERE a.regdno = :regdno
            ORDER BY a.appliedDate DESC
            """)
    List<Application> findByRegdnoWithDrive(@Param("regdno") String regdno);

    /**
     * Admin stats counters.
     */
    @Query("SELECT COUNT(a) FROM Application a")
    long countAll();

    @Query("SELECT COUNT(a) FROM Application a WHERE a.status = :status")
    long countByStatus(@Param("status") ApplicationStatus status);

    /**
     * Count applications per student — for StudentSummaryDTO.
     * Returns Object[] { regdno, totalCount, approvedCount }
     */
    @Query("""
            SELECT a.regdno,
                   COUNT(a.id),
                   SUM(CASE WHEN a.status = 'APPROVED' THEN 1 ELSE 0 END)
            FROM Application a
            GROUP BY a.regdno
            """)
    List<Object[]> findApplicationCountsPerStudent();
    
    /**
     * Counts ONLY for a specific list of regdnos — used by paginated getAllStudents().
     * Avoids scanning the entire applications table for a 20-student page.
     * Returns Object[] { regdno, totalCount, approvedCount }
     */
    @Query("""
            SELECT a.regdno,
                   COUNT(a.id),
                   SUM(CASE WHEN a.status = com.trident.placement.enums.ApplicationStatus.APPROVED THEN 1 ELSE 0 END)
            FROM Application a
            WHERE a.regdno IN :regdnos
            GROUP BY a.regdno
            """)
    List<Object[]> findApplicationCountsForStudents(@Param("regdnos") List<String> regdnos);
}