package com.trident.placement.dto.student;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentNotificationDTO {
    private Long id;
    private Long driveId;
    private String driveName;
    private String roundName;
    private String status;
    private String sentAt;
}
