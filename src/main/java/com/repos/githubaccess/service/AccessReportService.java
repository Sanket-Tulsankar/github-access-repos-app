package com.repos.githubaccess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.repos.githubaccess.config.GitHubProperties;
import com.repos.githubaccess.model.AccessReport;
import com.repos.githubaccess.model.RepoAccess;
import com.repos.githubaccess.model.UserAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
public class AccessReportService {
    private static final Logger log = LoggerFactory.getLogger(AccessReportService.class);

    private final GitHubApiClient apiClient;
    private final GitHubProperties props;
    private final ExecutorService executorService;

    public AccessReportService(GitHubApiClient apiClient,
                               GitHubProperties props,
                               ExecutorService githubExecutorService) {
        this.apiClient       = apiClient;
        this.props           = props;
        this.executorService = githubExecutorService;
    }

    @Cacheable(value = "accessReport")
    public AccessReport generateReport() {
        String org = props.getOrg();
        log.info("Cache miss — generating fresh access report for org: '{}'", org);
        return buildReport(org);
    }

    @CacheEvict(value = "accessReport",allEntries = true)
    public AccessReport refreshReport() {
        String org = props.getOrg();
        log.info("Cache evicted — regenerating access report for org: '{}'", org);
        return buildReport(org);
    }

    private AccessReport buildReport(String org) {

      List<JsonNode> repos = apiClient.fetchAllPages("/orgs/" + org + "/repos");
        log.info("Found {} repositories in org '{}'", repos.size(), org);

        Map<String, List<RepoAccess>> userAccessMap = new ConcurrentHashMap<>();
        Map<String, String>           userRoleMap   = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> futures = repos.stream()
                .map(repo -> CompletableFuture.runAsync(
                        () -> processRepo(repo, org, userAccessMap),
                        executorService
                ))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Completed collaborator lookups for all {} repos", repos.size());

        enrichWithOrgMembers(org, userRoleMap, userAccessMap);

        List<UserAccess> users = buildUserList(userAccessMap, userRoleMap);

        log.info("Report ready: {} users with access across {} repositories",
                users.size(), repos.size());

        return AccessReport.builder()
                .organization(org)
                .generatedAt(Instant.now())
                .totalRepositories(repos.size())
                .totalUsersWithAccess(users.size())
                .users(users)
                .build();
    }

    private void processRepo(JsonNode repo,
                             String org,
                             Map<String, List<RepoAccess>> userAccessMap) {
        String repoName     = repo.get("name").asText();
        String repoFullName = repo.get("full_name").asText();
        String visibility   = repo.has("visibility")
                ? repo.get("visibility").asText()
                : "unknown";

        log.debug("Fetching collaborators for: {}", repoFullName);

        try {
            List<JsonNode> collaborators = apiClient.fetchAllPages(
                    "/repos/" + org + "/" + repoName + "/collaborators"
            );

            for (JsonNode collaborator : collaborators) {
                String username   = collaborator.get("login").asText();
                String permission = extractHighestPermission(collaborator);

                RepoAccess repoAccess = RepoAccess.builder()
                        .repoName(repoName)
                        .repoFullName(repoFullName)
                        .visibility(visibility)
                        .permission(permission)
                        .build();

                userAccessMap
                        .computeIfAbsent(username, k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(repoAccess);
            }

            log.debug("Repo '{}': {} collaborators found", repoName, collaborators.size());

        } catch (Exception e) {
            log.warn("Skipping collaborators for repo '{}': {}", repoName, e.getMessage());
        }
    }

    private void enrichWithOrgMembers(String org,
                                      Map<String, String> userRoleMap,
                                      Map<String, List<RepoAccess>> userAccessMap) {
        try {
            List<JsonNode> members = apiClient.fetchAllPages("/orgs/" + org + "/members?role=all");
            for (JsonNode member : members) {
                String username = member.get("login").asText();
                String role     = member.has("role") ? member.get("role").asText() : "member";
                userRoleMap.put(username, role);
                userAccessMap.putIfAbsent(username, Collections.synchronizedList(new ArrayList<>()));
            }
            log.debug("Enriched with {} org members", members.size());
        } catch (Exception e) {
            log.warn("Could not fetch org members (direct collaborators still included): {}",
                    e.getMessage());
        }
    }

    private List<UserAccess> buildUserList(Map<String, List<RepoAccess>> userAccessMap,
                                           Map<String, String> userRoleMap) {
        return userAccessMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<RepoAccess> sortedRepos = entry.getValue().stream()
                            .sorted(Comparator.comparing(RepoAccess::getRepoName))
                            .collect(Collectors.toList());
                    return UserAccess.builder()
                            .username(entry.getKey())
                            .role(userRoleMap.getOrDefault(entry.getKey(), "outside_collaborator"))
                            .repositoryCount(sortedRepos.size())
                            .repositories(sortedRepos)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String extractHighestPermission(JsonNode collaborator) {
        if (!collaborator.has("permissions")) return "pull";
        JsonNode perms = collaborator.get("permissions");
        if (perms.has("admin")    && perms.get("admin").asBoolean())    return "admin";
        if (perms.has("maintain") && perms.get("maintain").asBoolean()) return "maintain";
        if (perms.has("push")     && perms.get("push").asBoolean())     return "push";
        if (perms.has("triage")   && perms.get("triage").asBoolean())   return "triage";
        return "pull";
    }
}
