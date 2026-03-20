package com.repos.githubaccess.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<Map<String, Object>> handleGitHubApiException(GitHubApiException ex) {
        log.error("GitHub API error: {} (githubStatus={})", ex.getMessage(), ex.getStatusCode());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("githubStatus", ex.getStatusCode());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(resolveHttpStatus(ex.getStatusCode())).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Internal server error: " + ex.getMessage());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(500).body(body);
    }

    private int resolveHttpStatus(int githubStatus) {
        switch (githubStatus) {
            case 401: return 401;
            case 403: return 403;
            case 404: return 404;
            default:  return 502;
        }
    }

}
