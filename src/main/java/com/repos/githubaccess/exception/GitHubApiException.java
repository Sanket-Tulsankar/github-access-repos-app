package com.repos.githubaccess.exception;

import lombok.Getter;

@Getter
public class GitHubApiException extends RuntimeException{

    private final int statusCode;

    public GitHubApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
