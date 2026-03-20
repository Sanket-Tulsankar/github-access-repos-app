# github-access-repos-app
# GitHub Organization Access Report Service

A Spring Boot REST API that connects to GitHub and generates a structured JSON report showing which users have access to which repositories within a given organization.

---

## How to Run the Project

### Step 1 — Install Prerequisites

Before running the project, make sure you have the following installed on your machine:

- **Java 11** — verify by running `java -version` in your terminal. You should see `11.x.x`.
- **Maven 3.6+** — verify by running `mvn -version`.
- **Docker** — used to run Redis locally. Verify by running `docker --version`.

### Step 2 — Start Redis

This project uses Redis as its cache store. Start a Redis container using Docker:

```
docker run -d --name redis -p 6379:6379 redis:7
```

Verify Redis is running:

```
docker exec -it redis redis-cli ping
```

You should see `PONG` as the response. If you see `PONG`, Redis is ready.

### Step 3 — Configure the Application

Open the file located at:

```
src/main/resources/application.yml
```

This is the main configuration file for the project. You need to fill in two values here — your GitHub token and your organization name. Find the `github` section and update it:

```yaml
github:
  token: ghp_your_actual_token_here       # paste your GitHub Personal Access Token here
  org: your-organization-name             # paste your GitHub organization name here
  api-base-url: https://api.github.com
  connect-timeout-ms: 5000
  read-timeout-ms: 10000
  max-concurrent-requests: 10
  cache-ttl-minutes: 10
```

Replace `ghp_your_actual_token_here` with your actual GitHub token (it starts with `ghp_`) and replace `your-organization-name` with the exact name of your GitHub organization as it appears in the URL — for example if your organization URL is `https://github.com/spring-projects` then the org name is `spring-projects`.

The Redis configuration is in the same `application.yml` file under the `spring.redis` section:

```yaml
spring:
  redis:
    host: localhost     # if Redis is running on your own machine, keep this as localhost
    port: 6379          # default Redis port, no need to change unless you changed it
    password:           # leave this blank if Redis has no password set
```

If you started Redis using the Docker command above, you do not need to change anything in the Redis section. The defaults will work as-is.

### Step 4 — Run the Application

Once Redis is running and `application.yml` is configured, start the application using Maven:

```
mvn spring-boot:run
```

You should see the following line in the console output, which confirms the application started successfully:

```
Started GitHubAccessReportApplication in 6.5 seconds
```

The application runs on port `8080` by default. If you want to change the port, find the `server` section in `application.yml` and update it:

```yaml
server:
  port: 8080    # change this to any port you prefer, e.g. 9090
```

### Step 5 — Test the Application

Open your browser and go to:

```
http://localhost:8080/api/v1/health
```

If the application is running correctly you will see:

```json
{"status": "UP"}
```

---

## How Authentication is Configured

This service authenticates with GitHub using a **Personal Access Token (PAT)**. The token is stored in `application.yml` under `github.token` and is automatically injected into every GitHub API request as a Bearer token in the Authorization header. You do not need to pass any credentials when calling the API endpoints — authentication is handled entirely on the server side.

### How to Create a GitHub Personal Access Token

1. Go to [https://github.com](https://github.com) and sign in to your account.
2. Click your profile picture in the top-right corner and select **Settings**.
3. In the left sidebar, scroll down and click **Developer settings**.
4. Click **Personal access tokens** and then click **Tokens (classic)**.
5. Click **Generate new token (classic)**.
6. Give the token a name such as `github-access-report` so you remember what it is for.
7. Under **Select scopes**, check the following two scopes:
   - `repo` — this allows the token to read private repositories and their collaborator lists.
   - `read:org` — this allows the token to read organization membership and member roles.
8. Click **Generate token** at the bottom of the page.
9. Copy the token immediately. GitHub only shows it once. It will look like `ghp_abc123XYZ456...`.

### Where to Put the Token

Once you have the token, open `src/main/resources/application.yml` and paste it as the value for `github.token`:

```yaml
github:
  token: ghp_abc123XYZ456your_token_here
```

Do not add any quotes around the token value. Do not commit this file to a public GitHub repository with the real token in it, as anyone who sees your token can access your GitHub data.

### How the Token is Used Internally

The token value from `application.yml` is loaded by the `GitHubProperties` class using Spring Boot's `@ConfigurationProperties`. It is then injected into `GitHubApiClient`, which adds it to every outgoing HTTP request as follows:

```
Authorization: Bearer ghp_your_token_here
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
```

---

## How to Call the API Endpoint

The service exposes three endpoints. All of them are available on `http://localhost:8080`.

### Endpoint 1 — GET /api/v1/access-report

This is the main endpoint. It returns the full access report showing which users have access to which repositories in your configured organization.

**Using a browser:**
Simply open the following URL in your browser:
```
http://localhost:8080/api/v1/access-report
```

**Using curl:**
```
curl http://localhost:8080/api/v1/access-report
```

**Using Postman:**
- Set the method to `GET`
- Enter the URL `http://localhost:8080/api/v1/access-report`
- Click Send

The first time you call this endpoint, the service fetches live data from GitHub, which may take a few seconds depending on how many repositories and users the organization has. The result is then stored in Redis. Every subsequent call within 10 minutes returns the cached result instantly without hitting GitHub again.

**Sample Response:**
```json
{
    "organization": "spring-projects",
    "generatedAt": "2026-03-20T14:03:11.092Z",
    "totalRepositories": 52,
    "totalUsersWithAccess": 120,
    "users": [
        {
            "username": "jhoeller",
            "role": "member",
            "repositoryCount": 3,
            "repositories": [
                {
                    "repoName": "spring-framework",
                    "repoFullName": "spring-projects/spring-framework",
                    "visibility": "public",
                    "permission": "push"
                }
            ]
        }
    ]
}
```

The `users` array lists every user who has access to at least one repository. For each user, `repositories` shows exactly which repositories they can access and with what permission level. The `permission` field shows the highest privilege that user has — `admin` is the highest, followed by `maintain`, `push`, `triage`, and `pull`.

### Endpoint 2 — POST /api/v1/access-report/refresh

This endpoint clears the Redis cache and immediately fetches fresh data from GitHub. Use this when you know that permissions have changed in your organization and you do not want to wait for the 10-minute cache to expire on its own.

**Using curl:**
```
curl -X POST http://localhost:8080/api/v1/access-report/refresh
```

**Using Postman:**
- Set the method to `POST`
- Enter the URL `http://localhost:8080/api/v1/access-report/refresh`
- Click Send

It returns the same response structure as the GET endpoint but with freshly fetched data.

### Endpoint 3 — GET /api/v1/health

A simple liveness check to verify the application is running.

```
curl http://localhost:8080/api/v1/health
```

Response:
```json
{"status": "UP"}
```

---

## Assumptions and Design Decisions

### Repo-First Approach Instead of User-First

The most obvious way to build this service would be to get all users in the organization and then for each user ask GitHub which repositories they can access. However this approach requires one API call per user, which means for an organization with 1000 users it would make 1000 sequential API calls. At that scale this would be extremely slow and would quickly hit GitHub's rate limits.

Instead this service takes a repo-first approach. It fetches all repositories in the organization first, and then for each repository it fetches the list of collaborators who have access to that repo. The result is then inverted to build a user-to-repo mapping. This approach requires only one API call per repository, so for an organization with 100 repositories and 1000 users it makes only 100 API calls instead of 1000.

### Parallel API Calls with a Bounded Thread Pool

The per-repository collaborator lookups all run in parallel rather than one at a time. This is done using Java's `CompletableFuture` with a fixed thread pool. The thread pool size is controlled by the `github.max-concurrent-requests` setting in `application.yml` (default is 10). This means up to 10 repository lookups happen simultaneously, which makes the report generation much faster for large organizations. The pool is kept bounded to avoid sending too many requests at once and triggering GitHub's secondary rate limits.

### Redis for Caching

The generated report is cached in Redis for 10 minutes. This means that the first call to the API fetches live data from GitHub and stores the result in Redis. All subsequent calls within 10 minutes return the cached result instantly without making any GitHub API calls at all. Redis was chosen over an in-memory cache because Redis survives application restarts — if you restart the service, the cache is still there. Redis is also shared across multiple instances of the service if you ever run more than one, whereas an in-memory cache is local to each instance.

The cache TTL is configurable via `github.cache-ttl-minutes` in `application.yml`. The `POST /api/v1/access-report/refresh` endpoint exists specifically to allow manual cache eviction when you need fresh data before the TTL expires.

### Graceful Handling of Inaccessible Repositories

For large organizations some repositories may be archived, restricted, or inaccessible with the given token. Rather than failing the entire report when one repository cannot be fetched, the service logs a warning for that repository and continues processing all other repositories. This means you always get a complete report for everything the token can access, even if a few repositories are skipped.

### Personal Account Support

The GitHub `/orgs/{org}/repos` endpoint only works for organization accounts, not personal accounts. To support testing with a personal GitHub account, the service automatically falls back to the `/users/{username}/repos` endpoint if the org endpoint returns a 404. Similarly for the members endpoint — if it fails for a personal account, the service fetches the single user profile instead.

### One Organization Per Instance

This service is designed to report on one GitHub organization at a time. The organization name is configured in `application.yml` and applies to all API calls. If you need reports for multiple organizations you would run separate instances of the service with different configuration.
