package com.qatools.commitanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qatools.commitanalyzer.model.AnalysisResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClaudeService {

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.model}")
    private String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ClaudeService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public AnalysisResult analyze(String diffText) throws IOException, InterruptedException {
        String truncated = diffText.length() > 12000
                ? diffText.substring(0, 12000) + "\n...[truncated]"
                : diffText;

        String prompt = "You are a QA engineer assistant. Analyze this git diff and determine if the changes are FUNCTIONAL or NON-FUNCTIONAL.\n\nFUNCTIONAL = changes that affect runtime behavior: business logic, APIs, data flow, user-facing features, bug fixes, performance, security.\nNON-FUNCTIONAL = changes that do NOT affect runtime behavior: whitespace, formatting, comments, documentation, renaming without logic change, dev config, build scripts, CI/CD.\n\nRespond ONLY in this JSON format, no markdown, no extra text:\n{\n  \"verdict\": \"FUNCTIONAL\" or \"NON-FUNCTIONAL\",\n  \"confidence\": \"HIGH\", \"MEDIUM\", or \"LOW\",\n  \"reason\": \"1-2 sentence plain English explanation\",\n  \"key_changes\": [\"short phrase 1\", \"short phrase 2\", \"short phrase 3\"]\n}\n\nGit diff:\n" + truncated;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 1000);
        body.put("temperature", 0);

        ArrayNode messages = body.putArray("messages");
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", "You are a QA engineer assistant. Always respond with valid JSON only.");

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("AI API error " + response.statusCode() + ": " + response.body());
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        String rawText = responseJson.path("choices").get(0).path("message").path("content").asText();
        String cleaned = rawText;
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```.*?\\R", "");
            cleaned = cleaned.replaceAll("(?s)\\R```.*$", "");
        }
        cleaned = cleaned.trim();
        JsonNode parsed = objectMapper.readTree(cleaned);

        AnalysisResult result = new AnalysisResult();
        result.setVerdict(parsed.path("verdict").asText());
        result.setConfidence(parsed.path("confidence").asText());
        result.setReason(parsed.path("reason").asText());

        List<String> keyChanges = new ArrayList<>();
        for (JsonNode kc : parsed.path("key_changes")) {
            keyChanges.add(kc.asText());
        }
        result.setKeyChanges(keyChanges);
        result.setRawDiffPreview(diffText.substring(0, Math.min(2000, diffText.length())));

        return result;
    }
}
