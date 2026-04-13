package com.trident.placement.repository;

import com.trident.placement.entity.DriveJDSelectionStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriveJDSelectionStepRepository extends JpaRepository<DriveJDSelectionStep, Long> {

    /**
     * Bulk-delete all steps for a DriveJD before re-inserting on update.
     * Faster than orphanRemoval on large step lists.
     */
    @Modifying
    @Query("DELETE FROM DriveJDSelectionStep s WHERE s.driveJD.id = :driveJdId")
    void deleteAllByDriveJdId(@Param("driveJdId") Long driveJdId);
}