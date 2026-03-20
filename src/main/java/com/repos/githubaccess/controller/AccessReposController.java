package com.repos.githubaccess.controller;

import com.repos.githubaccess.model.AccessReport;
import com.repos.githubaccess.service.AccessReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AccessReposController {

    private final AccessReportService accessReportService;

    public AccessReposController(AccessReportService accessReportService) {
        this.accessReportService = accessReportService;
    }

    @GetMapping("/access-report")
    public ResponseEntity<AccessReport> getAccessReport() {
        AccessReport report = accessReportService.generateReport();
        return ResponseEntity.ok(report);
    }

    @PostMapping("/access-report/refresh")
    public ResponseEntity<AccessReport> refreshAccessReport() {
        AccessReport report = accessReportService.refreshReport();
        return ResponseEntity.ok(report);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Collections.singletonMap("status", "UP"));
    }

}
