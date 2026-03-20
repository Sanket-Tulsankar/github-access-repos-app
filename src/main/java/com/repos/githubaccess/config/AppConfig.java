package com.repos.githubaccess.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AppConfig {


    private final GitHubProperties props;

    public AppConfig(GitHubProperties props) {
        this.props = props;
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService githubExecutorService() {
        return Executors.newFixedThreadPool(props.getMaxConcurrentRequests());
    }

}
