package com.qatools.commitanalyzer.model;

import lombok.Data;

@Data
public class BatchAnalysisResult {
    private int rowNumber;
    private String platform;
    private String owner;
    private String project;
    private String repo;
    private String commitA;
    private String commitB;
    private String verdict;
    private String confidence;
    private String reason;
    private String error;
}