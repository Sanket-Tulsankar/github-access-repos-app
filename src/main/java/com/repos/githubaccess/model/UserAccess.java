package com.repos.githubaccess.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccess implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;

    private String role;

    private int repositoryCount;

    private List<RepoAccess> repositories;
}
