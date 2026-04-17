//package com.trident.placement.service;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//import java.util.stream.Collectors;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import com.trident.placement.dto.DriveDTO;
//import com.trident.placement.entity.Drive;
//import com.trident.placement.enums.DriveStatus;
//import com.trident.placement.enums.DriveType;
//import com.trident.placement.repository.DriveRepository;
//import com.trident.placement.repository.StudentRepository;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class DriveServiceImpl implements DriveService {
//	
//	private final DriveRepository driveRepository;
//	private final StudentRepository studentRepository;
//	
//	@Autowired
//	private BputResultService bputResultService;
//	
//	private static final DateTimeFormatter Date_fmt = DateTimeFormatter.ofPattern("dd-MM-yy");
//	
//	@Override
//	public List<DriveDTO> getAllDrives() {
//        return driveRepository.findAll().stream()
//                .map(this::toDTO)
//                .collect(Collectors.toList());
//    }
//
//	@Override
//	public List<DriveDTO> getEligibleDrives(String regdno, String dob,
//	                                         String startSession, String endSession) {
//	    // Validate student exists
//	    studentRepository.findById(regdno)
//	            .orElseThrow(() -> new RuntimeException("Student not found: " + regdno));
//
//	    // Fetch CGPA from BPUT via the microservice
//	    BigDecimal cgpa = bputResultService.fetchAndCalculateCgpa(
//	            regdno, dob, startSession, endSession);
//
//	    if (cgpa == null) {
//	        log.warn("Could not fetch CGPA for {}. Returning no drives.", regdno);
//	        return List.of();
//	    }
//
//	    log.info("Student {} has CGPA: {}", regdno, cgpa);
//
//	    // Filter drives where student's CGPA >= drive's minimum CGPA
//	    return driveRepository.findAll().stream()
//	            .filter(d -> d.getStatus() == DriveStatus.OPEN)
//	            .filter(d -> !LocalDate.now().isAfter(d.getLastDate()))
//	            .filter(d -> cgpa.compareTo(d.getMinimumCgpa()) >= 0)
//	            .map(this::toDTO)
//	            .collect(Collectors.toList());
//	}
//	@Override
//	public List<DriveDTO> getDriveByType(String type) {
//		
//		DriveType driveType;
//		
//		try {
//			driveType = DriveType.valueOf(type.toUpperCase());
//		} catch (IllegalArgumentException e) {
//			throw new RuntimeException("Invalid Drive type : "+ type);
//		}
//		return driveRepository.findByDriveType(driveType)
//			.stream()
//			.map(this :: toDTO)
//			.collect(Collectors.toList());
//	}
//
//	@Override
//	public List<DriveDTO> getOpenDrives() {
//		return driveRepository.findByStatus(DriveStatus.OPEN)
//                .stream()
//                .map(this::toDTO)
//                .collect(Collectors.toList());
//	}
//	
//	private DriveDTO toDTO(Drive drive) {
//
//        return DriveDTO.builder()
//                .id(drive.getId())
//                .companyName(drive.getCompanyName())
//                .role(drive.getRole())
//                .type(drive.getDriveType().name())
//                .lpaPackage(drive.getLpaPackage())
//                .minimumCgpa(drive.getMinimumCgpa())
//                .lastDate(drive.getLastDate().format(Date_fmt))
//                .description(drive.getDescription())
//                .status(drive.getStatus().name())
//                .build();
//    }
//
//	@Override
//	public DriveDTO getDriveById(Long id) {
//		Drive drive = driveRepository.findById(id)
//				.orElseThrow(()-> new RuntimeException("Drive not found with id : " + id )); 
//		return toDTO(drive);
//	}
//}

package com.trident.placement.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.trident.placement.dto.DriveDTO;
import com.trident.placement.entity.Drive;
import com.trident.placement.enums.DriveStatus;
import com.trident.placement.enums.DriveType;
import com.trident.placement.repository.DriveRepository;
import com.trident.placement.repository.StudentRepository;
import com.trident.placement.service.CgpaEligibilityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriveServiceImpl implements DriveService {

    private final DriveRepository driveRepository;
    private final StudentRepository studentRepository;
    private final CgpaEligibilityService cgpaEligibilityService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yy");

    @Override
    public List<DriveDTO> getAllDrives() {
        return driveRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public DriveDTO getDriveById(Long id) {
        Drive drive = driveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Drive not found with id: " + id));
        return toDTO(drive);
    }

    /**
     * Returns OPEN drives that the student is eligible for based on CGPA.
     *
     * Queries the eligible_drives table — populated when admin creates a drive.
     * This is an instant DB query — NO call to BPUT API at runtime.
     *
     * If no eligibility records exist (drive was posted before this feature),
     * falls back to returning all open drives.
     */
    @Override
    public List<DriveDTO> getEligibleDrives(String regdno) {
        // Validate student exists
        studentRepository.findById(regdno)
                .orElseThrow(() -> new RuntimeException("Student not found: " + regdno));

        List<Drive> eligibleDrives = cgpaEligibilityService.getEligibleDrivesForStudent(regdno);

        // If the student has zero eligible drives, simply return an empty list.
        // We no longer fallback to returning ALL open drives because it bypasses branch
        // restrictions.
        if (eligibleDrives.isEmpty()) {
            log.debug("Student {} is not currently eligible for any active drives.", regdno);
            return List.of();
        }

        log.debug("Student {} is eligible for {} drives", regdno, eligibleDrives.size());
        return eligibleDrives.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<DriveDTO> getDriveByType(String type) {
        DriveType driveType;
        try {
            driveType = DriveType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid Drive type: " + type);
        }
        return driveRepository.findByDriveType(driveType)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<DriveDTO> getOpenDrives() {
        return driveRepository.findByStatus(DriveStatus.OPEN)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private DriveDTO toDTO(Drive drive) {
        return DriveDTO.builder()
                .id(drive.getId())
                .companyName(drive.getCompanyName())
                .role(drive.getRole())
                .type(drive.getDriveType().name())
                .lpaPackage(drive.getLpaPackage())
                .minimumCgpa(drive.getMinimumCgpa())
                .lastDate(drive.getLastDate().format(DATE_FMT))
                .description(drive.getDescription())
                .status(drive.getStatus().name())
                .eligibleBranches(drive.getEligibilityRows() == null ? List.of()
                        : drive.getEligibilityRows().stream()
                                .map(com.trident.placement.entity.DriveEligibility::getBranchCode)
                                .distinct().collect(Collectors.toList()))
                .build();
    }

}
