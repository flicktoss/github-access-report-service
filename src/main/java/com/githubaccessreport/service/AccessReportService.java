package com.githubaccessreport.service;

import com.githubaccessreport.client.GitHubClientPort;
import com.githubaccessreport.config.CacheConfig;
import com.githubaccessreport.model.dto.AccessReportResponse;
import com.githubaccessreport.model.dto.GitHubCollaborator;
import com.githubaccessreport.model.dto.GitHubRepository;
import com.githubaccessreport.model.dto.RepositoryAccess;
import com.githubaccessreport.model.dto.UserAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessReportService {

    private static final int MAX_CONCURRENCY = 10;

    private final GitHubClientPort gitHubClient;

    @Cacheable(value = CacheConfig.ACCESS_REPORT_CACHE, key = "#organization")
    public AccessReportResponse generateReport(String organization) {
        log.info("Generating access report for organization: {}", organization);
        long startTime = System.currentTimeMillis();

        List<GitHubRepository> repositories = gitHubClient.getRepositories(organization).block();

        if (repositories == null || repositories.isEmpty()) {
            log.info("No repositories found for organization: {}", organization);
            return AccessReportResponse.builder()
                    .organization(organization)
                    .generatedAt(Instant.now())
                    .users(List.of())
                    .build();
        }

        log.info("Processing {} repositories for organization: {}", repositories.size(), organization);

        // fetch collaborators for all repos in parallel
        List<Map.Entry<String, GitHubCollaborator>> repoCollaboratorPairs = Flux.fromIterable(repositories)
                .flatMap(repo -> gitHubClient.getCollaborators(organization, repo.getName())
                                .flatMapMany(Flux::fromIterable)
                                .<Map.Entry<String, GitHubCollaborator>>map(collaborator ->
                                        new AbstractMap.SimpleEntry<>(repo.getName(), collaborator)),
                        MAX_CONCURRENCY)
                .collectList()
                .block();

        // aggregate into user -> repositories mapping
        Map<String, List<RepositoryAccess>> userAccessMap = repoCollaboratorPairs == null
                ? Map.of()
                : repoCollaboratorPairs.stream()
                    .collect(Collectors.groupingBy(
                            entry -> entry.getValue().getLogin(),
                            Collectors.mapping(
                                    entry -> RepositoryAccess.builder()
                                            .name(entry.getKey())
                                            .permission(entry.getValue().getHighestPermission())
                                            .build(),
                                    Collectors.toList()
                            )
                    ));

        // build sorted response
        List<UserAccess> users = userAccessMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> UserAccess.builder()
                        .username(entry.getKey())
                        .repositories(entry.getValue().stream()
                                .sorted(Comparator.comparing(RepositoryAccess::getName))
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Access report generated for organization '{}': {} users across {} repositories in {}ms",
                organization, users.size(), repositories.size(), elapsed);

        return AccessReportResponse.builder()
                .organization(organization)
                .generatedAt(Instant.now())
                .users(users)
                .build();
    }
}
