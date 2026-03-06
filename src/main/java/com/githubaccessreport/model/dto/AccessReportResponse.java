package com.githubaccessreport.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessReportResponse {

    private String organization;
    private Instant generatedAt;
    private List<UserAccess> users;
}
