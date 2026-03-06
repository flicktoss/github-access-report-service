# GitHub Repository Access Report Service

Spring Boot service that connects to the GitHub API and generates reports showing which users have access to which repositories within a given organization.

## Tech Stack

- Java 17, Spring Boot 3
- Spring WebClient (reactive, non-blocking)
- Maven, Lombok, Jackson
- SLF4J + Logback
- SpringDoc OpenAPI (Swagger)

## Prerequisites

- Java 17+
- Maven 3.8+
- GitHub Personal Access Token (classic) with `repo` and `read:org` scopes

## Getting Started

### 1. Clone and build

```bash
git clone <repo-url>
cd github-access-report-service
mvn clean install
```

### 2. Configure authentication

Generate a [Personal Access Token (classic)](https://github.com/settings/tokens) with `repo` + `read:org` scopes, then set it as an environment variable:

```bash
export GITHUB_TOKEN=ghp_your_token_here
```

The app reads this from `application.yml` via `${GITHUB_TOKEN}`.

### 3. Run

```bash
mvn spring-boot:run
```

App starts on port `8080`.

## API

### `GET /api/access-report/{organization}`

```bash
curl http://localhost:8080/api/access-report/my-org
```

**Response:**

```json
{
  "organization": "my-org",
  "generatedAt": "2024-01-15T10:30:00Z",
  "users": [
    {
      "username": "alice",
      "repositories": [
        { "name": "backend-service", "permission": "admin" },
        { "name": "frontend-ui", "permission": "write" }
      ]
    },
    {
      "username": "bob",
      "repositories": [
        { "name": "backend-service", "permission": "read" }
      ]
    }
  ]
}
```

### Error Responses

| Status | Scenario |
|--------|----------|
| 401 | Invalid token |
| 404 | Org not found |
| 429 | Rate limit exceeded |
| 502 | GitHub API error |

All errors return a consistent JSON body with `status`, `error`, `message`, and `timestamp`.

### Swagger UI

Available at [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

## Project Structure

```
src/main/java/com/githubaccessreport/
├── GithubAccessReportApplication.java
├── client/
│   ├── GitHubClient.java
│   └── GitHubClientPort.java
├── config/
│   ├── CacheConfig.java
│   ├── OpenApiConfig.java
│   └── WebClientConfig.java
├── controller/
│   └── AccessReportController.java
├── exception/
│   ├── GitHubApiException.java
│   ├── GlobalExceptionHandler.java
│   ├── OrganizationNotFoundException.java
│   └── RateLimitExceededException.java
├── model/dto/
│   ├── AccessReportResponse.java
│   ├── ErrorResponse.java
│   ├── GitHubCollaborator.java
│   ├── GitHubRepository.java
│   ├── RepositoryAccess.java
│   └── UserAccess.java
└── service/
    └── AccessReportService.java
```

## Design Decisions

- **WebClient over RestTemplate** — WebClient is the recommended non-blocking HTTP client in Spring Boot 3. It enables parallel API calls using Project Reactor.

- **Parallel collaborator fetching** — uses `Flux.flatMap` with concurrency of 10 to fetch collaborators across repos simultaneously instead of one-by-one. This is critical for orgs with 100+ repos.

- **Automatic pagination** — GitHub caps responses at 100 items/page. The client recursively fetches until a page has fewer than 100 items.

- **Caching** — `@Cacheable` with `ConcurrentMapCacheManager` avoids redundant API calls for the same org. Could be swapped for Redis in production.

- **Interface-based client** — `GitHubClientPort` interface makes the service testable with Mockito without needing byte-buddy class instrumentation.

- **Graceful 403 handling** — if the token lacks push access to a repo, the collaborators call returns 403. Instead of failing the whole report, those repos are skipped.

## Assumptions

1. Token has `repo` + `read:org` scopes
2. The org is accessible to the authenticated user
3. Permission hierarchy: `admin > maintain > push > triage > pull`
4. Single instance deployment (in-memory cache)
5. GitHub's rate limit of 5000 req/hour is sufficient

## Running Tests

```bash
mvn test
```

12 tests across 3 test classes:
- `AccessReportServiceTest` — service logic and aggregation
- `AccessReportControllerTest` — web layer with MockMvc
- `GitHubClientTest` — API client with MockWebServer
