package com.trident.placement.service;

import com.trident.placement.dto.ApplicationDTO;
import java.util.List;

public interface ApplicationService {

    List<ApplicationDTO> getStudentApplications(String regdno);

    List<ApplicationDTO> getStudentApplicationsByStatus(String regdno, String status);

    ApplicationDTO applyToDrive(String regdno, Long driveId);

}