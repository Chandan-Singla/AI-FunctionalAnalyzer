package com.qatools.commitanalyzer.controller;

import com.qatools.commitanalyzer.model.*;
import com.qatools.commitanalyzer.service.BitbucketService;
import com.qatools.commitanalyzer.service.ClaudeService;
import com.qatools.commitanalyzer.service.GitHubService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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
            result = runAnalysis(req);
        } catch (Exception e) {
            result = new AnalysisResult();
            result.setError(e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/analyze/batch")
    public ResponseEntity<List<BatchAnalysisResult>> analyzeBatch(
            @RequestParam("file") MultipartFile file) {

        List<BatchAnalysisResult> results = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {

            String headerLine = reader.readLine();
            if (headerLine == null) return ResponseEntity.badRequest().build();

            String line;
            int rowNum = 1;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                BatchAnalysisResult batchResult = new BatchAnalysisResult();
                batchResult.setRowNumber(rowNum++);

                try {
                    String[] cols = parseCsvLine(line);

                    if (cols.length < 5) {
                        batchResult.setError("Row has fewer than 5 columns. Expected: platform,owner/project,repo,commitA,commitB");
                        results.add(batchResult);
                        continue;
                    }

                    AnalyzeRequest req = new AnalyzeRequest();
                    req.setPlatform(cols[0].trim());
                    req.setOwner(cols[1].trim());
                    req.setProject(cols[1].trim());
                    req.setRepo(cols[2].trim());
                    req.setCommitA(cols[3].trim());
                    req.setCommitB(cols[4].trim());
                    req.setHost(cols.length >= 6 && !cols[5].isBlank() ? cols[5].trim() : defaultHost);

                    batchResult.setPlatform(req.getPlatform());
                    batchResult.setOwner(req.getOwner());
                    batchResult.setRepo(req.getRepo());
                    batchResult.setCommitA(req.getCommitA());
                    batchResult.setCommitB(req.getCommitB());

                    AnalysisResult analysis = runAnalysis(req);
                    batchResult.setVerdict(analysis.getVerdict());
                    batchResult.setConfidence(analysis.getConfidence());
                    batchResult.setReason(analysis.getReason());
                    if (analysis.getError() != null) batchResult.setError(analysis.getError());

                } catch (Exception e) {
                    batchResult.setError(e.getMessage());
                }

                results.add(batchResult);
                Thread.sleep(500);
            }

        } catch (Exception e) {
            BatchAnalysisResult err = new BatchAnalysisResult();
            err.setError("Failed to read CSV: " + e.getMessage());
            results.add(err);
        }

        return ResponseEntity.ok(results);
    }

    private AnalysisResult runAnalysis(AnalyzeRequest req) throws Exception {
        String diff;
        String platform = req.getPlatform() == null ? "bitbucket" : req.getPlatform().toLowerCase();

        if ("github".equals(platform)) {
            if (req.getOwner() == null || req.getOwner().isBlank())
                throw new IllegalArgumentException("GitHub owner is required.");
            if (req.getRepo() == null || req.getRepo().isBlank())
                throw new IllegalArgumentException("Repository name is required.");
            diff = gitHubService.fetchDiff(req.getOwner(), req.getRepo(), req.getCommitA(), req.getCommitB());
        } else if ("bitbucket".equals(platform)) {
            if (req.getHost() == null || req.getHost().isBlank()) req.setHost(defaultHost);
            if (req.getProject() == null || req.getProject().isBlank())
                throw new IllegalArgumentException("Bitbucket project key is required.");
            diff = bitbucketService.fetchDiff(req.getHost(), req.getProject(), req.getRepo(), req.getCommitA(), req.getCommitB());
        } else {
            throw new IllegalArgumentException("Unknown platform: " + platform + ". Use 'github' or 'bitbucket'.");
        }

        if (diff == null || diff.isBlank()) {
            AnalysisResult r = new AnalysisResult();
            r.setError("No diff found - commits may be identical.");
            return r;
        }

        return claudeService.analyze(diff);
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') { inQuotes = !inQuotes; }
            else if (c == ',' && !inQuotes) { fields.add(current.toString()); current = new StringBuilder(); }
            else { current.append(c); }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}