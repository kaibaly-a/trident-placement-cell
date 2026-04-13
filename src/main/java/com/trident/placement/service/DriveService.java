package com.trident.placement.service;

import java.util.List;

import com.trident.placement.dto.DriveDTO;

public interface DriveService {
	
	List<DriveDTO> getAllDrives();
	DriveDTO getDriveById(Long id);
//	List<DriveDTO> getEligibleDrives(String regdno, String dob, String startSession, String endSession);
	List<DriveDTO> getEligibleDrives(String regdno);
	List<DriveDTO> getDriveByType(String type);
	List<DriveDTO> getOpenDrives();
}
