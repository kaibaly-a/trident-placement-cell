package com.trident.placement.repository;

import com.trident.placement.entity.DriveEligibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DriveEligibilityRepository extends JpaRepository<DriveEligibility, Long> {

    /**
     * Returns all eligibility rows for a drive (one per branch).
     * Used by CgpaEligibilityService to know which branches to filter students by.
     */
    List<DriveEligibility> findByDriveId(Long driveId);

    /**
     * Deletes all eligibility rows for a drive.
     * Called when a drive is deleted or updated with new branches.
     */
    @Modifying
    @Query("DELETE FROM DriveEligibility de WHERE de.drive.id = :driveId")
    void deleteByDriveId(@Param("driveId") Long driveId);
}
