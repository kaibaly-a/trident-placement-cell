package com.trident.placement.service;

import com.trident.placement.dto.DashboardStatsDTO;
import com.trident.placement.dto.StudentDTO;

public interface StudentService {

    StudentDTO getStudentById(String regdno);

    StudentDTO getStudentByEmail(String email);

    DashboardStatsDTO getDashboardStats(String regdno);

    StudentDTO updateProfile(String regdno, StudentDTO updates);

}
