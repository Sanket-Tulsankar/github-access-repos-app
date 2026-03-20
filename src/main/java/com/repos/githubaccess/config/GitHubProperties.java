package com.repos.githubaccess.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    private String token;
    private String apiBaseUrl;
    private String org;
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private int maxConcurrentRequests;

    private int cacheTtlMinutes = 10;

}
