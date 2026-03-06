package com.githubaccessreport.service;

import com.githubaccessreport.client.GitHubClientPort;
import com.githubaccessreport.model.dto.AccessReportResponse;
import com.githubaccessreport.model.dto.GitHubCollaborator;
import com.githubaccessreport.model.dto.GitHubRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessReportServiceTest {

    @Mock
    private GitHubClientPort gitHubClient;

    @InjectMocks
    private AccessReportService accessReportService;

    private static final String TEST_ORG = "test-org";

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
    }

    @Test
    @DisplayName("Should generate report with correct user-to-repository mapping")
    void shouldGenerateReportWithCorrectMapping() {
        // Arrange
        GitHubRepository repo1 = GitHubRepository.builder()
                .name("backend-service")
                .fullName("test-org/backend-service")
                .owner(GitHubRepository.Owner.builder().login(TEST_ORG).build())
                .build();

        GitHubRepository repo2 = GitHubRepository.builder()
                .name("frontend-ui")
                .fullName("test-org/frontend-ui")
                .owner(GitHubRepository.Owner.builder().login(TEST_ORG).build())
                .build();

        GitHubCollaborator alice = GitHubCollaborator.builder()
                .login("alice")
                .permissions(Map.of("admin", true, "push", true, "pull", true))
                .build();

        GitHubCollaborator bob = GitHubCollaborator.builder()
                .login("bob")
                .permissions(Map.of("admin", false, "push", true, "pull", true))
                .build();

        when(gitHubClient.getRepositories(TEST_ORG))
                .thenReturn(Mono.just(List.of(repo1, repo2)));

        when(gitHubClient.getCollaborators(TEST_ORG, "backend-service"))
                .thenReturn(Mono.just(List.of(alice, bob)));

        when(gitHubClient.getCollaborators(TEST_ORG, "frontend-ui"))
                .thenReturn(Mono.just(List.of(alice)));

        // Act
        AccessReportResponse report = accessReportService.generateReport(TEST_ORG);

        // Assert
        assertThat(report).isNotNull();
        assertThat(report.getOrganization()).isEqualTo(TEST_ORG);
        assertThat(report.getGeneratedAt()).isNotNull();
        assertThat(report.getUsers()).hasSize(2);

        // Alice should have access to both repos
        var aliceAccess = report.getUsers().stream()
                .filter(u -> u.getUsername().equals("alice"))
                .findFirst()
                .orElseThrow();
        assertThat(aliceAccess.getRepositories()).hasSize(2);
        assertThat(aliceAccess.getRepositories())
                .anyMatch(r -> r.getName().equals("backend-service") && r.getPermission().equals("admin"))
                .anyMatch(r -> r.getName().equals("frontend-ui") && r.getPermission().equals("admin"));

        // Bob should have access to one repo
        var bobAccess = report.getUsers().stream()
                .filter(u -> u.getUsername().equals("bob"))
                .findFirst()
                .orElseThrow();
        assertThat(bobAccess.getRepositories()).hasSize(1);
        assertThat(bobAccess.getRepositories().get(0).getName()).isEqualTo("backend-service");
        assertThat(bobAccess.getRepositories().get(0).getPermission()).isEqualTo("write");

        verify(gitHubClient).getRepositories(TEST_ORG);
        verify(gitHubClient, times(2)).getCollaborators(eq(TEST_ORG), anyString());
    }

    @Test
    @DisplayName("Should return empty users list when organization has no repositories")
    void shouldReturnEmptyUsersWhenNoRepositories() {
        // Arrange
        when(gitHubClient.getRepositories(TEST_ORG))
                .thenReturn(Mono.just(List.of()));

        // Act
        AccessReportResponse report = accessReportService.generateReport(TEST_ORG);

        // Assert
        assertThat(report).isNotNull();
        assertThat(report.getOrganization()).isEqualTo(TEST_ORG);
        assertThat(report.getUsers()).isEmpty();

        verify(gitHubClient).getRepositories(TEST_ORG);
        verify(gitHubClient, never()).getCollaborators(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle repository with no collaborators")
    void shouldHandleRepositoryWithNoCollaborators() {
        // Arrange
        GitHubRepository repo = GitHubRepository.builder()
                .name("empty-repo")
                .fullName("test-org/empty-repo")
                .owner(GitHubRepository.Owner.builder().login(TEST_ORG).build())
                .build();

        when(gitHubClient.getRepositories(TEST_ORG))
                .thenReturn(Mono.just(List.of(repo)));

        when(gitHubClient.getCollaborators(TEST_ORG, "empty-repo"))
                .thenReturn(Mono.just(List.of()));

        // Act
        AccessReportResponse report = accessReportService.generateReport(TEST_ORG);

        // Assert
        assertThat(report).isNotNull();
        assertThat(report.getUsers()).isEmpty();
    }

    @Test
    @DisplayName("Should sort users alphabetically by username")
    void shouldSortUsersAlphabetically() {
        // Arrange
        GitHubRepository repo = GitHubRepository.builder()
                .name("repo")
                .fullName("test-org/repo")
                .owner(GitHubRepository.Owner.builder().login(TEST_ORG).build())
                .build();

        GitHubCollaborator charlie = GitHubCollaborator.builder()
                .login("charlie")
                .permissions(Map.of("pull", true))
                .build();

        GitHubCollaborator alice = GitHubCollaborator.builder()
                .login("alice")
                .permissions(Map.of("pull", true))
                .build();

        GitHubCollaborator bob = GitHubCollaborator.builder()
                .login("bob")
                .permissions(Map.of("pull", true))
                .build();

        when(gitHubClient.getRepositories(TEST_ORG))
                .thenReturn(Mono.just(List.of(repo)));

        when(gitHubClient.getCollaborators(TEST_ORG, "repo"))
                .thenReturn(Mono.just(List.of(charlie, alice, bob)));

        // Act
        AccessReportResponse report = accessReportService.generateReport(TEST_ORG);

        // Assert
        assertThat(report.getUsers()).hasSize(3);
        assertThat(report.getUsers().get(0).getUsername()).isEqualTo("alice");
        assertThat(report.getUsers().get(1).getUsername()).isEqualTo("bob");
        assertThat(report.getUsers().get(2).getUsername()).isEqualTo("charlie");
    }
}
