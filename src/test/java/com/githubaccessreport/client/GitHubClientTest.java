package com.githubaccessreport.client;

import com.githubaccessreport.exception.OrganizationNotFoundException;
import com.githubaccessreport.exception.RateLimitExceededException;
import com.githubaccessreport.model.dto.GitHubCollaborator;
import com.githubaccessreport.model.dto.GitHubRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GitHubClient using MockWebServer to simulate GitHub API responses.
 */
class GitHubClientTest {

    private MockWebServer mockWebServer;
    private GitHubClient gitHubClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        gitHubClient = new GitHubClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Should fetch repositories successfully")
    void shouldFetchRepositories() {
        // Arrange - return less than 100 items to indicate single page
        String responseBody = """
                [
                    {
                        "id": 1,
                        "name": "repo-1",
                        "full_name": "test-org/repo-1",
                        "private": false,
                        "owner": { "login": "test-org" }
                    },
                    {
                        "id": 2,
                        "name": "repo-2",
                        "full_name": "test-org/repo-2",
                        "private": true,
                        "owner": { "login": "test-org" }
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act & Assert
        StepVerifier.create(gitHubClient.getRepositories("test-org"))
                .assertNext(repos -> {
                    assertThat(repos).hasSize(2);
                    assertThat(repos.get(0).getName()).isEqualTo("repo-1");
                    assertThat(repos.get(1).getName()).isEqualTo("repo-2");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch collaborators successfully")
    void shouldFetchCollaborators() {
        // Arrange
        String responseBody = """
                [
                    {
                        "login": "alice",
                        "permissions": { "admin": true, "push": true, "pull": true }
                    },
                    {
                        "login": "bob",
                        "permissions": { "admin": false, "push": true, "pull": true }
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act & Assert
        StepVerifier.create(gitHubClient.getCollaborators("test-org", "repo-1"))
                .assertNext(collabs -> {
                    assertThat(collabs).hasSize(2);
                    assertThat(collabs.get(0).getLogin()).isEqualTo("alice");
                    assertThat(collabs.get(0).getHighestPermission()).isEqualTo("admin");
                    assertThat(collabs.get(1).getLogin()).isEqualTo("bob");
                    assertThat(collabs.get(1).getHighestPermission()).isEqualTo("write");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw OrganizationNotFoundException on 404")
    void shouldThrowOrganizationNotFoundOn404() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"message\": \"Not Found\"}"));

        // Act & Assert
        StepVerifier.create(gitHubClient.getRepositories("nonexistent-org"))
                .expectError(OrganizationNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Should throw RateLimitExceededException on 403 with rate limit headers")
    void shouldThrowRateLimitExceededOn403() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1700000000")
                .setBody("{\"message\": \"API rate limit exceeded\"}"));

        // Act & Assert
        StepVerifier.create(gitHubClient.getRepositories("test-org"))
                .expectError(RateLimitExceededException.class)
                .verify();
    }
}
