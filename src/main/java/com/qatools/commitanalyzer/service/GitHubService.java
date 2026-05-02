package com.qatools.commitanalyzer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class GitHubService {

    @Value("${github.token:}")
    private String githubToken;

    private final HttpClient httpClient;

    public GitHubService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Fetches unified diff between two commits from GitHub.
     * owner = GitHub username or org (e.g. "torvalds")
     * repo  = repo name (e.g. "linux")
     */
    public String fetchDiff(String owner, String repo, String commitA, String commitB)
            throws IOException, InterruptedException {

        // GitHub compare API returns unified diff when Accept is text/plain
        String url = String.format(
                "https://api.github.com/repos/%s/%s/compare/%s...%s",
                owner, repo, commitA, commitB
        );

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github.v3.diff")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .timeout(Duration.ofSeconds(30));

        // Add token if provided (required for private repos, raises rate limit for public)
        if (githubToken != null && !githubToken.isBlank()) {
            builder.header("Authorization", "Bearer " + githubToken);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            throw new IOException("Repository or commits not found. Check owner, repo name, and commit hashes.");
        } else if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IOException("Authentication required. Add a GitHub token in application.properties.");
        } else if (response.statusCode() == 422) {
            throw new IOException("Commits are too far apart or not comparable. Try commits on the same branch.");
        } else if (response.statusCode() != 200) {
            throw new IOException("GitHub API error " + response.statusCode() + ": " + response.body().substring(0, Math.min(300, response.body().length())));
        }

        String diff = response.body();
        if (diff == null || diff.isBlank()) {
            throw new IOException("No diff returned — the two commits may be identical.");
        }

        return diff;
    }
}