package com.cityledger.cityledger.dto;

import com.cityledger.cityledger.model.Complaint;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ComplaintWithReportCount {
    private Complaint complaint;
    private long reportCount; // Number of people who reported the same issue
}
