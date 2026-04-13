package com.trident.placement.repository;

import com.trident.placement.entity.DriveJD;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DriveJDRepository extends JpaRepository<DriveJD, Long> {

    /**
     * Fetch DriveJD by its parent Drive ID.
     * Eagerly loads selectionSteps to avoid N+1 on detail views.
     */
    @Query("""
            SELECT jd FROM DriveJD jd
            LEFT JOIN FETCH jd.selectionSteps
            WHERE jd.drive.id = :driveId
            """)
    Optional<DriveJD> findByDriveIdWithSteps(@Param("driveId") Long driveId);

    /**
     * Lightweight existence check.
     * FIXED: Changed from derived existsByDriveId to COUNT query
     * to avoid Oracle 11g ORA-00933 "FETCH FIRST ? ROWS ONLY" error.
     */
    @Query("SELECT COUNT(jd) > 0 FROM DriveJD jd WHERE jd.drive.id = :driveId")
    boolean existsByDriveId(@Param("driveId") Long driveId);
}