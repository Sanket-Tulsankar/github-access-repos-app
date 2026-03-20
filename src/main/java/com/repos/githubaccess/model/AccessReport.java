package com.repos.githubaccess.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AccessReport implements Serializable {

    private static final long serialVersionUID = 1L;

    private String organization;

    private Instant generatedAt;

    private int totalRepositories;

    private int totalUsersWithAccess;

    private List<UserAccess> users;

}
