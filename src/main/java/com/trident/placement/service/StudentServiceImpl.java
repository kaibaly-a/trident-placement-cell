package com.trident.placement.service;

import org.springframework.stereotype.Service;

import com.trident.placement.dto.DashboardStatsDTO;
import com.trident.placement.dto.StudentDTO;
import com.trident.placement.entity.Student;
import com.trident.placement.repository.DriveRepository;
import com.trident.placement.repository.StudentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentServiceImpl implements StudentService {
	
	private final StudentRepository studentRepository;
	private final DriveRepository driveRepository;
	
	@Override
	public StudentDTO getStudentById(String regdno) {

		log.info("Fetching student by regdno: {}", regdno);

		Student student = studentRepository.findById(regdno)
                .orElseThrow(() -> {
                    log.error("Student not found with regdno: {}", regdno);
                    return new RuntimeException("Student not found with id: " + regdno);
                });

		log.info("Student found: {}", student.getName());

        return toDTO(student);
	}

	@Override
	public StudentDTO getStudentByEmail(String email) {

		log.info("Fetching student by email/upn: {}", email);

		Student student = studentRepository.findByMsUserPrincipalName(email)
		        .or(() -> studentRepository.findByEmail(email))
                .orElseThrow(() -> {
                    log.error("Student not found with email/upn: {}", email);
                    return new RuntimeException("Student not found with email: " + email);
                });

		log.info("Student found: {}", student.getName());

        return toDTO(student);
	}

	@Override
	public DashboardStatsDTO getDashboardStats(String regdno) {

		log.info("Fetching dashboard stats for regdno: {}", regdno);

		Student student = studentRepository.findById(regdno)
                .orElseThrow(() -> {
                    log.error("Student not found while fetching dashboard: {}", regdno);
                    return new RuntimeException("Student not found");
                });

        long eligible = driveRepository.count();
        log.debug("Eligible drives count: {}", eligible);

        DashboardStatsDTO stats = DashboardStatsDTO.builder()
                .eligibleDrives(eligible)
                .build();

        log.info("Dashboard stats prepared for student: {}", student.getName());

        return stats;
	}

	@Override
	public StudentDTO updateProfile(String regdno, StudentDTO updates) {

		log.info("Updating profile for regdno: {}", regdno);

		Student student = studentRepository.findById(regdno)
                .orElseThrow(() -> {
                    log.error("Student not found for update: {}", regdno);
                    return new RuntimeException("Student not found");
                });

        if (updates.getName() != null) {
            log.debug("Updating name: {}", updates.getName());
            student.setName(updates.getName());
        }

        if (updates.getEmail() != null) {
            log.debug("Updating email: {}", updates.getEmail());
            student.setEmail(updates.getEmail());
        }

        if (updates.getPhno() != null) {
            log.debug("Updating phone: {}", updates.getPhno());
            student.setPhno(updates.getPhno());
        }

        Student savedStudent = studentRepository.save(student);

        log.info("Profile updated successfully for: {}", savedStudent.getName());

        return toDTO(savedStudent);
	}
	
	private StudentDTO toDTO(Student student) {

		log.debug("Mapping Student entity to DTO for regdno: {}", student.getRegdno());

        return StudentDTO.builder()
                .regdno(student.getRegdno())
                .name(student.getName())
                .gender(student.getGender())
                .dob(student.getDob())
                .course(student.getCourse())
                .branchCode(student.getBranchCode())
                .admissionYear(student.getAdmissionYear())
                .degreeYop(student.getDegreeYop())
                .phno(student.getPhno())
                .email(student.getEmail())
                .studentType(student.getStudentType())
                .hostelier(student.getHostelier())
                .transportAvailed(student.getTransportAvailed())
                .status(student.getStatus())
                .batchId(student.getBatchId())
                .currentYear(student.getCurrentYear())
                .aadharno(student.getAadhaarno())
                .indortrng(student.getIndortrng())
                .plpoolm(student.getPlpoolm())
                .cfpaymode(student.getCfpaymode())
                .religion(student.getReligion())
                .msUserPrincipalName(student.getMsUserPrincipalName())
                .collegeName(student.getCollegeName())
                .build();
    }
}