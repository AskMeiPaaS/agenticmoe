package com.ayedata.agenticmoe.core;

import com.ayedata.agenticmoe.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class VoyageClient {
    private final HttpClient httpClient;
    private final AppConfig config;
    private final ObjectMapper jsonMapper;

    public VoyageClient(AppConfig config) {
        this.config = config;
        this.jsonMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Overloaded method to support the AgentService's optimized routing call.
     */
    public CompletableFuture<float[]> embedQuery(String text, String model, int targetDim, List<String> traceLogs) {
        String payload = """
                { "input": "%s", "model": "%s" }
                """.formatted(escapeJson(text), model);

        log("[VoyageClient] Embedding Request: URL=" + config.getEmbeddingUrl() + ", Model=" + model
                + ", Dim=" + targetDim + ", Payload Length=" + payload.length(), traceLogs);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getEmbeddingUrl()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseVectorResponse(response, targetDim, traceLogs));
    }

    /**
     * Call the LLM Chat Completion API
     */
    public CompletableFuture<String> chatCompletion(String systemPrompt, String userMessage, String model) {
        String payload = """
                {
                    "model": "%s",
                    "messages": [
                        {"role": "system", "content": "%s"},
                        {"role": "user", "content": "%s"}
                    ]
                }
                """.formatted(model, escapeJson(systemPrompt), escapeJson(userMessage));

        System.out.println("[VoyageClient] Chat Completion Request: URL=" + config.getChatUrl() + ", Model=" + model
                + ", Payload Length=" + payload.length());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getChatUrl()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseChatResponse);
    }

    /**
     * Standard method for Expert Execution (Full Dimension).
     */
    public CompletableFuture<float[]> embedQuery(String text, String model) {
        return embedQuery(text, model, -1, null); // -1 indicates no truncation
    }

    /**
     * Standard method for Specific Dimension without trace logs.
     */
    public CompletableFuture<float[]> embedQuery(String text, String model, int targetDim) {
        return embedQuery(text, model, targetDim, null);
    }

    private float[] parseVectorResponse(HttpResponse<String> response, int targetDim, List<String> traceLogs) {
        log("[VoyageClient] Vector Response: Status=" + response.statusCode(), traceLogs);
        if (response.statusCode() != 200) {
            System.err.println("[VoyageClient] Vector API Error Response Body: " + response.body());
            throw new RuntimeException("Voyage API Failed: " + response.statusCode());
        }
        try {
            JsonNode root = jsonMapper.readTree(response.body());
            JsonNode embeddingNode = root.path("data").get(0).path("embedding");

            int originalSize = embeddingNode.size();
            // MRL Slicing: Use the smaller of targetDim or originalSize
            int finalSize = (targetDim > 0 && targetDim < originalSize) ? targetDim : originalSize;

            float[] vector = new float[finalSize];
            for (int i = 0; i < finalSize; i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }
            log("[VoyageClient] Sliced Vector to Dimension: " + finalSize, traceLogs);
            return vector;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse vector response", e);
        }
    }

    private String parseChatResponse(HttpResponse<String> response) {
        System.out.println("[VoyageClient] Chat Response: Status=" + response.statusCode());
        if (response.statusCode() != 200) {
            System.err.println("[VoyageClient] Chat API Error Response Body: " + response.body());
            throw new RuntimeException("Voyage Chat API Failed: " + response.statusCode() + " - " + response.body());
        }
        try {
            JsonNode root = jsonMapper.readTree(response.body());
            String textResponse = root.path("choices").get(0).path("message").path("content").asText();
            System.out.println("[VoyageClient] Parsed Chat Completion Length: " + textResponse.length() + " chars.");
            return textResponse;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse chat response", e);
        }
    }

    private String escapeJson(String input) {
        return input.replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void log(String message, List<String> traceLogs) {
        System.out.println(message);
        if (traceLogs != null) {
            traceLogs.add(message);
        }
    }
}