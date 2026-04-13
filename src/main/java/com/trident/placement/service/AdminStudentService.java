package com.trident.placement.service;

import com.trident.placement.dto.StudentDTO;
import com.trident.placement.dto.admin.AdminApplicationResponse;
import com.trident.placement.dto.admin.AdminStatsDTO;
import com.trident.placement.dto.admin.StudentSummaryDTO;

import java.util.List;

import org.springframework.data.domain.Page;

public interface AdminStudentService {
    AdminStatsDTO getAdminStats();
//List<StudentSummaryDTO> getAllStudents();
    List<StudentSummaryDTO> searchStudents(String query);
    StudentDTO getStudentProfile(String regdno);
    List<AdminApplicationResponse> getStudentApplicationHistory(String regdno);
	Page<StudentSummaryDTO> getAllStudents(int page, int size);
}