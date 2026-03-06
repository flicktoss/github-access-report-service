package com.githubaccessreport.client;

import com.githubaccessreport.exception.GitHubApiException;
import com.githubaccessreport.exception.OrganizationNotFoundException;
import com.githubaccessreport.exception.RateLimitExceededException;
import com.githubaccessreport.model.dto.GitHubCollaborator;
import com.githubaccessreport.model.dto.GitHubRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class GitHubClient implements GitHubClientPort {

    private static final int PER_PAGE = 100;

    private final WebClient webClient;

    public GitHubClient(WebClient gitHubWebClient) {
        this.webClient = gitHubWebClient;
    }

    public Mono<List<GitHubRepository>> getRepositories(String organization) {
        log.info("Fetching repositories for organization: {}", organization);
        return fetchAllPages(
                "/orgs/{org}/repos?per_page={perPage}&page={page}",
                GitHubRepository.class,
                organization
        ).collectList()
         .doOnSuccess(repos -> log.info("Fetched {} repositories for organization: {}", repos.size(), organization));
    }

    public Mono<List<GitHubCollaborator>> getCollaborators(String owner, String repo) {
        log.debug("Fetching collaborators for repository: {}/{}", owner, repo);
        return fetchAllPages(
                "/repos/{owner}/{repo}/collaborators?per_page={perPage}&page={page}",
                GitHubCollaborator.class,
                owner, repo
        ).collectList()
         .doOnSuccess(collabs -> log.debug("Fetched {} collaborators for {}/{}", collabs.size(), owner, repo))
         .onErrorResume(GitHubApiException.class, ex -> {
             if (ex.getStatusCode().value() == 403) {
                 log.debug("Access denied for collaborators on {}/{}, skipping", owner, repo);
                 return Mono.just(List.of());
             }
             return Mono.error(ex);
         })
         .onErrorResume(OrganizationNotFoundException.class, ex -> {
             log.debug("Repository {}/{} not found, skipping", owner, repo);
             return Mono.just(List.of());
         });
    }

    private <T> Flux<T> fetchAllPages(String uriTemplate, Class<T> responseType, Object... uriVars) {
        return fetchPage(uriTemplate, responseType, 1, uriVars);
    }

    private <T> Flux<T> fetchPage(String uriTemplate, Class<T> responseType, int page, Object... baseUriVars) {
        Object[] uriVars = new Object[baseUriVars.length + 2];
        System.arraycopy(baseUriVars, 0, uriVars, 0, baseUriVars.length);
        uriVars[baseUriVars.length] = PER_PAGE;
        uriVars[baseUriVars.length + 1] = page;

        return webClient.get()
                .uri(uriTemplate, uriVars)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> handleClientError(response.statusCode(), response.headers().asHttpHeaders()))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new GitHubApiException("GitHub API server error", response.statusCode())))
                .bodyToFlux(responseType)
                .collectList()
                .flatMapMany(items -> {
                    if (items.size() < PER_PAGE) {
                        return Flux.fromIterable(items);
                    }
                    return Flux.fromIterable(items)
                            .concatWith(fetchPage(uriTemplate, responseType, page + 1, baseUriVars));
                });
    }

    private Mono<? extends Throwable> handleClientError(HttpStatusCode statusCode, HttpHeaders headers) {
        int status = statusCode.value();

        if (status == 404) {
            return Mono.error(new OrganizationNotFoundException("The requested resource was not found on GitHub"));
        }

        if (status == 403) {
            String rateLimitRemaining = headers.getFirst("X-RateLimit-Remaining");
            if ("0".equals(rateLimitRemaining)) {
                String resetTime = headers.getFirst("X-RateLimit-Reset");
                return Mono.error(new RateLimitExceededException(
                        "GitHub API rate limit exceeded. Resets at epoch: " + resetTime));
            }
            return Mono.error(new GitHubApiException("Access forbidden. Check your token permissions.", statusCode));
        }

        if (status == 401) {
            return Mono.error(new GitHubApiException(
                    "Authentication failed. Verify your GitHub Personal Access Token.", statusCode));
        }

        return Mono.error(new GitHubApiException("GitHub API error (HTTP " + status + ")", statusCode));
    }
}
