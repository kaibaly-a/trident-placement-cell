package com.trident.placement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trident.placement.entity.Drive;
import com.trident.placement.enums.DriveStatus;
import com.trident.placement.enums.DriveType;

public interface DriveRepository extends JpaRepository<Drive, Long> {
	
	List<Drive> findByStatus(DriveStatus status);
	List<Drive> findByDriveType(DriveType driveType);
	
}
