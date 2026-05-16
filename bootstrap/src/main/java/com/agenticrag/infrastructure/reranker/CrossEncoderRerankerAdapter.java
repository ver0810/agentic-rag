package com.agenticrag.infrastructure.reranker;

import com.agenticrag.infra.ai.port.reranker.RerankerPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CrossEncoderRerankerAdapter implements RerankerPort {

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final RetryTemplate retryTemplate;

    public CrossEncoderRerankerAdapter(String apiUrl, String apiKey, String model, 
                                      ObjectMapper objectMapper, HttpClient httpClient,
                                      RetryTemplate retryTemplate) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = (model != null && !model.isBlank()) ? model : "bge-reranker-v2-m3";
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.retryTemplate = retryTemplate;
    }

    @Override
    public List<RerankResult> rerank(String query, List<RerankCandidate> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("query", query);
            body.put("documents", candidates.stream().map(RerankCandidate::content).toList());
            body.put("top_n", topK);
            body.put("return_documents", false);

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = retryTemplate.execute(context -> 
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            );

            if (response.statusCode() != 200) {
                log.error("Cross-encoder API returned {}: {}", response.statusCode(), response.body());
                return fallbackToOriginalScores(candidates, topK);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.get("results");

            if (results == null || !results.isArray()) {
                log.error("Unexpected cross-encoder response format: {}", response.body());
                return fallbackToOriginalScores(candidates, topK);
            }

            List<RerankResult> reranked = new ArrayList<>();
            for (JsonNode result : results) {
                int index = result.get("index").asInt();
                float score = (float) result.get("relevance_score").asDouble();
                RerankCandidate candidate = candidates.get(index);
                reranked.add(new RerankResult(candidate.chunkId(), candidate.content(), score, candidate.metadata()));
            }

            return reranked;
        } catch (Exception e) {
            log.error("Cross-encoder rerank failed, falling back to original scores: {}", e.getMessage());
            return fallbackToOriginalScores(candidates, topK);
        }
    }

    private List<RerankResult> fallbackToOriginalScores(List<RerankCandidate> candidates, int topK) {
        return candidates.stream()
                .sorted(Comparator.comparing(RerankCandidate::originalScore).reversed())
                .limit(topK)
                .map(c -> new RerankResult(c.chunkId(), c.content(), c.originalScore(), c.metadata()))
                .toList();
    }
}
