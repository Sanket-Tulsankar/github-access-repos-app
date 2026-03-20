package com.repos.githubaccess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repos.githubaccess.config.GitHubProperties;
import com.repos.githubaccess.exception.GitHubApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private static final int PAGE_SIZE = 100;

    private final HttpClient httpClient;
    private final GitHubProperties props;
    private final ObjectMapper objectMapper;

    public GitHubApiClient(HttpClient httpClient, GitHubProperties props) {
        this.httpClient = httpClient;
        this.props = props;
        this.objectMapper = new ObjectMapper();
    }

    public List<JsonNode> fetchAllPages(String endpoint) {
        List<JsonNode> results = new ArrayList<>();
        String url = buildUrl(endpoint) + (endpoint.contains("?") ? "&" : "?")
                + "per_page=" + PAGE_SIZE + "&page=1";

        while (url != null) {
            HttpResponse<String> response = executeGet(url);
            List<JsonNode> page = parseArray(response.body());
            results.addAll(page);
            log.debug("Fetched {} items from {}, running total: {}", page.size(), url, results.size());
            url = extractNextPageUrl(response);
        }
        return results;
    }

    public JsonNode fetchSingle(String endpoint) {
        HttpResponse<String> response = executeGet(buildUrl(endpoint));
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new GitHubApiException("Failed to parse response from: " + endpoint, 400);
        }
    }

    private HttpResponse<String> executeGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                    .header("Authorization", "Bearer " + props.getToken())
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );
            validateStatus(response, url);
            return response;

        } catch (GitHubApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubApiException("Network error calling GitHub API: " + url, 400);
        }
    }

    private void validateStatus(HttpResponse<String> response, String url) {
        int status = response.statusCode();
        if (status == 200 || status == 201) return;

        if (status == 401) {
            throw new GitHubApiException(
                    "GitHub authentication failed. Check your GITHUB_TOKEN.", 401);
        }
        if (status == 403) {
            throw new GitHubApiException(
                    "GitHub rate limit exceeded or insufficient token scope.", 403);
        }
        if (status == 404) {
            throw new GitHubApiException(
                    "Resource not found: " + url + ". Check org name and token scope.", 404);
        }
        if (status >= 400) {
            throw new GitHubApiException(
                    "GitHub API returned HTTP " + status + " for: " + url, status);
        }
    }

    private List<JsonNode> parseArray(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            List<JsonNode> list = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(list::add);
            }
            return list;
        } catch (Exception e) {
            throw new GitHubApiException("Failed to parse paginated JSON response", 400);
        }
    }

    private String extractNextPageUrl(HttpResponse<String> response) {
        return response.headers()
                .firstValue("Link")
                .map(linkHeader -> {
                    for (String part : linkHeader.split(",")) {
                        if (part.contains("rel=\"next\"")) {
                            int start = part.indexOf('<') + 1;
                            int end   = part.indexOf('>');
                            if (start > 0 && end > start) {
                                return part.substring(start, end).trim();
                            }
                        }
                    }
                    return null;
                })
                .orElse(null);
    }

    private String buildUrl(String endpoint) {
        if (endpoint.startsWith("http")) return endpoint;
        return props.getApiBaseUrl() + endpoint;
    }

}
