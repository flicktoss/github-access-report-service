package com.githubaccessreport.exception;

import com.githubaccessreport.model.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrganizationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationNotFound(OrganizationNotFoundException ex) {
        log.warn("Organization not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.error("GitHub API rate limit exceeded: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<ErrorResponse> handleGitHubApiException(GitHubApiException ex) {
        log.error("GitHub API error (status={}): {}", ex.getStatusCode(), ex.getMessage());

        HttpStatus status = ex.getStatusCode().value() == 401
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.BAD_GATEWAY;

        return buildErrorResponse(status, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later."
        );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(status).body(errorResponse);
    }
}
