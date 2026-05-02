package com.qatools.commitanalyzer.model;

import lombok.Data;
import java.util.List;

@Data
public class AnalysisResult {
    private String verdict;       // "FUNCTIONAL" or "NON-FUNCTIONAL"
    private String confidence;    // "HIGH", "MEDIUM", "LOW"
    private String reason;
    private List<String> keyChanges;
    private String rawDiffPreview;
    private String error;
}
