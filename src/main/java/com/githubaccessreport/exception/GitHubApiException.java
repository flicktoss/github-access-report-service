package com.githubaccessreport.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class GitHubApiException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public GitHubApiException(String message, HttpStatusCode statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitHubApiException(String message, HttpStatusCode statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
