package com.trident.placement.repository;

import com.trident.placement.entity.Drive;
import com.trident.placement.enums.DriveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Admin-specific drive queries.
 *
 * NOTE: This repository manages the same Drive entity as DriveRepository.
 * Spring Data JPA allows multiple repositories for the same entity.
 * AdminDriveRepository adds admin-only aggregate queries (counts per drive)
 * without modifying the student-facing DriveRepository.
 *
 * Both repositories are valid Spring beans — Spring Data creates separate
 * proxy instances. There is no conflict.
 */
@Repository
public interface AdminDriveRepository extends JpaRepository<Drive, Long> {

    /**
     * Returns aggregate application counts per drive in ONE query.
     * Used to build AdminDriveResponse without N+1 queries.
     *
     * Returns List of Object[]:
     *   [0] = drive ID        (Long)
     *   [1] = total apps      (Long)
     *   [2] = shortlisted     (Long)
     *   [3] = approved        (Long)
     */
    @Query("""
            SELECT d.id,
                   COUNT(a.id),
                   SUM(CASE WHEN a.status = com.trident.placement.enums.ApplicationStatus.SHORTLISTED THEN 1 ELSE 0 END),
                   SUM(CASE WHEN a.status = com.trident.placement.enums.ApplicationStatus.APPROVED    THEN 1 ELSE 0 END)
            FROM Drive d
            LEFT JOIN Application a ON a.drive.id = d.id
            GROUP BY d.id
            """)
    List<Object[]> findAllDrivesWithCounts();

    /**
     * Application counts for a single drive.
     * Returns single Object[]:
     *   [0] = total apps  (Long)
     *   [1] = shortlisted (Long)
     *   [2] = approved    (Long)
     */
    @Query("""
            SELECT COUNT(a.id),
                   SUM(CASE WHEN a.status = com.trident.placement.enums.ApplicationStatus.SHORTLISTED THEN 1 ELSE 0 END),
                   SUM(CASE WHEN a.status = com.trident.placement.enums.ApplicationStatus.APPROVED    THEN 1 ELSE 0 END)
            FROM Application a
            WHERE a.drive.id = :driveId
            """)
    List<Object[]> findApplicationCountsByDriveId(@Param("driveId") Long driveId);

    /**
     * Count drives by status — used in admin stats.
     */
    long countByStatus(DriveStatus status);

    /**
     * Returns all drive IDs — lightweight query used for bulk eligibility recompute.
     */
    @Query("SELECT d.id FROM Drive d")
    List<Long> findAllIds();
}