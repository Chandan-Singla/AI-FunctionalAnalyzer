package com.qatools.commitanalyzer.controller;

import com.qatools.commitanalyzer.model.AnalyzeRequest;
import com.qatools.commitanalyzer.model.AnalysisResult;
import com.qatools.commitanalyzer.service.BitbucketService;
import com.qatools.commitanalyzer.service.ClaudeService;
import com.qatools.commitanalyzer.service.GitHubService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController 
@RequestMapping("/api")
public class AnalyzerController {

    private final BitbucketService bitbucketService;
    private final GitHubService gitHubService;
    private final ClaudeService claudeService;

    @Value("${bitbucket.default.host}")
    private String defaultHost;

    public AnalyzerController(BitbucketService bitbucketService,
                               GitHubService gitHubService,
                               ClaudeService claudeService) {
        this.bitbucketService = bitbucketService;
        this.gitHubService = gitHubService;
        this.claudeService = claudeService;
    }

    @GetMapping("/default-host")
    public ResponseEntity<String> getDefaultHost() {
        return ResponseEntity.ok(defaultHost);
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResult> analyze(@RequestBody AnalyzeRequest req) {
        AnalysisResult result = new AnalysisResult();
        try {
            String diff = null;
            String platform = req.getPlatform() == null ? "bitbucket" : req.getPlatform().toLowerCase();

           if ("github".equals(platform)) {
    if (req.getOwner() == null || req.getOwner().isBlank())
        throw new IllegalArgumentException("GitHub owner (username/org) is required.");
    if (req.getRepo() == null || req.getRepo().isBlank())
        throw new IllegalArgumentException("Repository name is required.");

    diff = gitHubService.fetchDiff(req.getOwner(), req.getRepo(), req.getCommitA(), req.getCommitB());
    
}
 else if ("bitbucket".equals(platform)) {
                if (req.getHost() == null || req.getHost().isBlank()) req.setHost(defaultHost);
                if (req.getProject() == null || req.getProject().isBlank())
                    throw new IllegalArgumentException("Bitbucket project key is required.");

                diff = bitbucketService.fetchDiff(
                        req.getHost(), req.getProject(), req.getRepo(),
                        req.getCommitA(), req.getCommitB()
                );
            }

            if (diff == null || diff.isBlank()) {
                result.setError("No diff found — the two commits may be identical.");
                return ResponseEntity.ok(result);
            }

            result = claudeService.analyze(diff);

        } catch (Exception e) {
            result = new AnalysisResult();
            result.setError(e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}