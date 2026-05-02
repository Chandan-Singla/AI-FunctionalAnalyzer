package com.qatools.commitanalyzer.model;

import lombok.Data;

@Data
public class AnalyzeRequest {
    // "github" or "bitbucket"
    private String platform;

    // GitHub fields
    private String owner;   // GitHub username or org

    // Bitbucket Server fields
    private String host;
    private String project;

    // Shared
    private String repo;
    private String commitA;
    private String commitB;
}