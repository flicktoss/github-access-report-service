package com.githubaccessreport.client;

import com.githubaccessreport.model.dto.GitHubCollaborator;
import com.githubaccessreport.model.dto.GitHubRepository;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GitHubClientPort {

    Mono<List<GitHubRepository>> getRepositories(String organization);

    Mono<List<GitHubCollaborator>> getCollaborators(String owner, String repo);
}
