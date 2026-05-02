package com.qatools.commitanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class BitbucketService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BitbucketService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String fetchDiff(String host, String project, String repo, String commitA, String commitB)
            throws IOException, InterruptedException {

        String cleanHost = host.replaceAll("/$", "");
       String url = String.format(
        "%s/rest/api/1.0/projects/%s/repos/%s/diff?since=%s&until=%s&contextLines=3&withComments=false",
        cleanHost, project.toUpperCase(), repo, commitA, commitB
);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            throw new IOException("Repository or commits not found. Check project key, repo slug, and commit hashes.");
        } else if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IOException("Authentication required. This repository may not be fully public.");
        } else if (response.statusCode() != 200) {
            throw new IOException("Bitbucket Server returned HTTP " + response.statusCode() + ": " + response.body().substring(0, Math.min(200, response.body().length())));
        }

        JsonNode root = objectMapper.readTree(response.body());
        return buildUnifiedDiff(root);
    }

    private String buildUnifiedDiff(JsonNode root) {
        StringBuilder sb = new StringBuilder();
        JsonNode diffs = root.path("diffs");

        if (diffs.isEmpty()) {
            return "";
        }

        for (JsonNode fileDiff : diffs) {
            String src = fileDiff.path("source").path("toString").asText("/dev/null");
            String dst = fileDiff.path("destination").path("toString").asText("/dev/null");
            sb.append("--- ").append(src).append("\n");
            sb.append("+++ ").append(dst).append("\n");

            for (JsonNode hunk : fileDiff.path("hunks")) {
                sb.append(String.format("@@ -%d,%d +%d,%d @@\n",
                        hunk.path("sourceLine").asInt(),
                        hunk.path("sourceSpan").asInt(),
                        hunk.path("destinationLine").asInt(),
                        hunk.path("destinationSpan").asInt()));

                for (JsonNode segment : hunk.path("segments")) {
                    String type = segment.path("type").asText();
                    String prefix = switch (type) {
                        case "ADDED" -> "+";
                        case "REMOVED" -> "-";
                        default -> " ";
                    };
                    for (JsonNode line : segment.path("lines")) {
                        sb.append(prefix).append(line.path("line").asText()).append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
