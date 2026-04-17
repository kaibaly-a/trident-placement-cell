package com.trident.placement.dto.student;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class StudentNotificationResponse {
    private List<StudentNotificationDTO> data;
    private long total;
    private int page;
    private int pageSize;
}
