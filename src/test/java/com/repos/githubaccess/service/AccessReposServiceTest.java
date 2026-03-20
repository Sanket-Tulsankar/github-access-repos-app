package com.repos.githubaccess.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repos.githubaccess.config.GitHubProperties;
import com.repos.githubaccess.model.AccessReport;
import com.repos.githubaccess.model.UserAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccessReposServiceTest {

    @Mock
    private GitHubApiClient apiClient;

    private AccessReportService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        GitHubProperties props = new GitHubProperties();
        props.setOrg("test-org");
        props.setToken("test-token");
        props.setApiBaseUrl("https://api.github.com");
        props.setMaxConcurrentRequests(5);
        props.setConnectTimeoutMs(5000);
        props.setReadTimeoutMs(10000);

        service = new AccessReportService(
                apiClient,
                props,
                Executors.newFixedThreadPool(5)
        );
    }

    @Test
    @DisplayName("Should return correct user and repo data for a single repo with one collaborator")
    void generateReport_singleRepoSingleUser_returnsCorrectData() throws Exception {

        JsonNode repo         = mapper.readTree(
                "{\"name\":\"repo-1\",\"full_name\":\"test-org/repo-1\",\"visibility\":\"private\"}");
        JsonNode collaborator = mapper.readTree(
                "{\"login\":\"alice\",\"permissions\":{\"admin\":false,\"push\":true,\"pull\":true}}");
        JsonNode member       = mapper.readTree(
                "{\"login\":\"alice\",\"role\":\"member\"}");

        when(apiClient.fetchAllPages(contains("/orgs/test-org/repos")))
                .thenReturn(Collections.singletonList(repo));
        when(apiClient.fetchAllPages(contains("/collaborators")))
                .thenReturn(Collections.singletonList(collaborator));
        when(apiClient.fetchAllPages(contains("/members")))
                .thenReturn(Collections.singletonList(member));

        AccessReport report = service.generateReport();

        assertThat(report.getOrganization()).isEqualTo("test-org");
        assertThat(report.getTotalRepositories()).isEqualTo(1);
        assertThat(report.getTotalUsersWithAccess()).isEqualTo(1);
        assertThat(report.getGeneratedAt()).isNotNull();

        UserAccess alice = report.getUsers().get(0);
        assertThat(alice.getUsername()).isEqualTo("alice");
        assertThat(alice.getRole()).isEqualTo("member");
        assertThat(alice.getRepositoryCount()).isEqualTo(1);

        assertThat(alice.getRepositories()).hasSize(1);
        assertThat(alice.getRepositories().get(0).getRepoName()).isEqualTo("repo-1");
        assertThat(alice.getRepositories().get(0).getPermission()).isEqualTo("push");
        assertThat(alice.getRepositories().get(0).getVisibility()).isEqualTo("private");
    }

    @Test
    @DisplayName("Should correctly pick 'admin' as highest permission when admin flag is true")
    void generateReport_adminPermission_extractedCorrectly() throws Exception {
        JsonNode repo         = mapper.readTree(
                "{\"name\":\"repo-1\",\"full_name\":\"test-org/repo-1\",\"visibility\":\"public\"}");
        JsonNode collaborator = mapper.readTree(
                "{\"login\":\"bob\",\"permissions\":{\"admin\":true,\"push\":true,\"pull\":true}}");

        when(apiClient.fetchAllPages(contains("/orgs/test-org/repos")))
                .thenReturn(Collections.singletonList(repo));
        when(apiClient.fetchAllPages(contains("/collaborators")))
                .thenReturn(Collections.singletonList(collaborator));
        when(apiClient.fetchAllPages(contains("/members")))
                .thenReturn(Collections.emptyList());

        AccessReport report = service.generateReport();

        assertThat(report.getUsers().get(0).getRepositories().get(0).getPermission())
                .isEqualTo("admin");
    }

    @Test
    @DisplayName("Should aggregate multiple repos for the same user")
    void generateReport_multipleRepos_userAggregatedCorrectly() throws Exception {
        JsonNode repo1 = mapper.readTree(
                "{\"name\":\"repo-1\",\"full_name\":\"test-org/repo-1\",\"visibility\":\"private\"}");
        JsonNode repo2 = mapper.readTree(
                "{\"name\":\"repo-2\",\"full_name\":\"test-org/repo-2\",\"visibility\":\"public\"}");
        JsonNode collaborator = mapper.readTree(
                "{\"login\":\"alice\",\"permissions\":{\"push\":true,\"pull\":true}}");

        when(apiClient.fetchAllPages(contains("/orgs/test-org/repos")))
                .thenReturn(Arrays.asList(repo1, repo2));

        when(apiClient.fetchAllPages(contains("/collaborators")))
                .thenReturn(Collections.singletonList(collaborator));
        when(apiClient.fetchAllPages(contains("/members")))
                .thenReturn(Collections.emptyList());

        AccessReport report = service.generateReport();

        assertThat(report.getTotalRepositories()).isEqualTo(2);
        assertThat(report.getTotalUsersWithAccess()).isEqualTo(1);

        UserAccess alice = report.getUsers().get(0);
        assertThat(alice.getRepositoryCount()).isEqualTo(2);
        assertThat(alice.getRepositories()).extracting("repoName")
                .containsExactlyInAnyOrder("repo-1", "repo-2");
    }

    @Test
    @DisplayName("Should assign 'outside_collaborator' role to users not in org members list")
    void generateReport_userNotInMembersList_getsOutsideCollaboratorRole() throws Exception {
        JsonNode repo         = mapper.readTree(
                "{\"name\":\"repo-1\",\"full_name\":\"test-org/repo-1\",\"visibility\":\"private\"}");
        JsonNode collaborator = mapper.readTree(
                "{\"login\":\"external-dev\",\"permissions\":{\"pull\":true}}");

        when(apiClient.fetchAllPages(contains("/orgs/test-org/repos")))
                .thenReturn(Collections.singletonList(repo));
        when(apiClient.fetchAllPages(contains("/collaborators")))
                .thenReturn(Collections.singletonList(collaborator));
        when(apiClient.fetchAllPages(contains("/members")))
                .thenReturn(Collections.emptyList());

        AccessReport report = service.generateReport();

        assertThat(report.getUsers().get(0).getRole()).isEqualTo("outside_collaborator");
    }

    @Test
    @DisplayName("Should skip inaccessible repo and still return the rest of the report")
    void generateReport_collaboratorFetchFails_gracefulDegradation() throws Exception {
        JsonNode repo = mapper.readTree(
                "{\"name\":\"forbidden-repo\",\"full_name\":\"test-org/forbidden-repo\",\"visibility\":\"private\"}");

        when(apiClient.fetchAllPages(contains("/orgs/test-org/repos")))
                .thenReturn(Collections.singletonList(repo));
        when(apiClient.fetchAllPages(contains("/collaborators")))
                .thenThrow(new RuntimeException("403 Forbidden"));
        when(apiClient.fetchAllPages(contains("/members")))
                .thenReturn(Collections.emptyList());

        AccessReport report = service.generateReport();

        assertThat(report).isNotNull();
        assertThat(report.getTotalRepositories()).isEqualTo(1);
        assertThat(report.getTotalUsersWithAccess()).isEqualTo(0);
        assertThat(report.getUsers()).isEmpty();
    }

    @Test
    @DisplayName("Should return empty report for an org with no repositories")
    void generateReport_emptyOrg_returnsEmptyReport() throws Exception {
        when(apiClient.fetchAllPages(contains("/orgs/test-org/repos")))
                .thenReturn(Collections.emptyList());
        when(apiClient.fetchAllPages(contains("/members")))
                .thenReturn(Collections.emptyList());

        AccessReport report = service.generateReport();

        assertThat(report.getTotalRepositories()).isEqualTo(0);
        assertThat(report.getTotalUsersWithAccess()).isEqualTo(0);
        assertThat(report.getUsers()).isEmpty();
    }

    @Test
    @DisplayName("Users list should be sorted alphabetically by username")
    void generateReport_usersAreSortedAlphabetically() throws Exception {
        JsonNode repo = mapper.readTree(
                "{\"name\":\"repo-1\",\"full_name\":\"test-org/repo-1\",\"visibility\":\"public\"}");
        JsonNode charlie = mapper.readTree(
                "{\"login\":\"charlie\",\"permissions\":{\"pull\":true}}");
        JsonNode alice = mapper.readTree(
                "{\"login\":\"alice\",\"permissions\":{\"push\":true,\"pull\":true}}");
        JsonNode bob = mapper.readTree(
                "{\"login\":\"bob\",\"permissions\":{\"push\":true,\"pull\":true}}");

        when(apiClient.fetchAllPages(contains("/orgs/test-org/repos")))
                .thenReturn(Collections.singletonList(repo));
        when(apiClient.fetchAllPages(contains("/collaborators")))
                .thenReturn(Arrays.asList(charlie, alice, bob));
        when(apiClient.fetchAllPages(contains("/members")))
                .thenReturn(Collections.emptyList());

        AccessReport report = service.generateReport();

        List<UserAccess> users = report.getUsers();
        assertThat(users).extracting("username")
                .containsExactly("alice", "bob", "charlie");
    }

    @Test
    @DisplayName("Should handle missing org members endpoint gracefully")
    void generateReport_membersFetchFails_stillReturnsDirectCollaborators() throws Exception {
        JsonNode repo         = mapper.readTree(
                "{\"name\":\"repo-1\",\"full_name\":\"test-org/repo-1\",\"visibility\":\"private\"}");
        JsonNode collaborator = mapper.readTree(
                "{\"login\":\"alice\",\"permissions\":{\"push\":true,\"pull\":true}}");

        when(apiClient.fetchAllPages(contains("/orgs/test-org/repos")))
                .thenReturn(Collections.singletonList(repo));
        when(apiClient.fetchAllPages(contains("/collaborators")))
                .thenReturn(Collections.singletonList(collaborator));
        when(apiClient.fetchAllPages(contains("/members")))
                .thenThrow(new RuntimeException("403 Forbidden"));

        AccessReport report = service.generateReport();

        assertThat(report.getTotalUsersWithAccess()).isEqualTo(1);
        assertThat(report.getUsers().get(0).getUsername()).isEqualTo("alice");
        assertThat(report.getUsers().get(0).getRole()).isEqualTo("outside_collaborator");
    }

}
