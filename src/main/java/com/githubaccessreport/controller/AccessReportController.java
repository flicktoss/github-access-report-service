package com.githubaccessreport.controller;

import com.githubaccessreport.model.dto.AccessReportResponse;
import com.githubaccessreport.model.dto.ErrorResponse;
import com.githubaccessreport.service.AccessReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/access-report")
@RequiredArgsConstructor
@Tag(name = "Access Report", description = "GitHub organization access report operations")
public class AccessReportController {

    private final AccessReportService accessReportService;

    @Operation(summary = "Generate Access Report",
            description = "Generates a report showing which users have access to which repositories "
                    + "within the specified GitHub organization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report generated successfully",
                    content = @Content(schema = @Schema(implementation = AccessReportResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid GitHub authentication token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "GitHub API rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "GitHub API error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{organization}")
    public ResponseEntity<AccessReportResponse> getAccessReport(
            @Parameter(description = "GitHub organization login name", example = "google")
            @PathVariable String organization) {

        log.info("Received access report request for organization: {}", organization);
        AccessReportResponse report = accessReportService.generateReport(organization);
        return ResponseEntity.ok(report);
    }
}
