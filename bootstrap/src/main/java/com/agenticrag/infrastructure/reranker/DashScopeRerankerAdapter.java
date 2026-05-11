package com.agenticrag.infrastructure.reranker;

import com.agenticrag.infra.ai.port.reranker.RerankerPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

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
public class DashScopeRerankerAdapter implements RerankerPort {

    private static final String DEFAULT_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-reranking/text-reranking";

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DashScopeRerankerAdapter(String apiUrl, String apiKey, String model, ObjectMapper objectMapper) {
        this.apiUrl = apiUrl != null ? apiUrl : DEFAULT_API_URL;
        this.apiKey = apiKey;
        this.model = model != null ? model : "gte-rerank";
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<RerankResult> rerank(String query, List<RerankCandidate> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);

            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("documents", candidates.stream().map(RerankCandidate::content).toList());
            body.put("input", input);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("top_n", topK);
            parameters.put("return_documents", false);
            body.put("parameters", parameters);

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("DashScope rerank API returned {}: {}", response.statusCode(), response.body());
                return fallbackToOriginalScores(candidates, topK);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode output = root.get("output");
            if (output == null) {
                log.error("Missing 'output' in DashScope rerank response: {}", response.body());
                return fallbackToOriginalScores(candidates, topK);
            }

            JsonNode results = output.get("results");
            if (results == null || !results.isArray()) {
                log.error("Missing 'results' in DashScope rerank response: {}", response.body());
                return fallbackToOriginalScores(candidates, topK);
            }

            List<RerankResult> reranked = new ArrayList<>();
            for (JsonNode result : results) {
                int index = result.get("index").asInt();
                float score = (float) result.get("relevance_score").asDouble();
                if (index >= 0 && index < candidates.size()) {
                    RerankCandidate candidate = candidates.get(index);
                    reranked.add(new RerankResult(candidate.chunkId(), candidate.content(), score, candidate.metadata()));
                }
            }

            return reranked;
        } catch (Exception e) {
            log.error("DashScope rerank failed, falling back to original scores: {}", e.getMessage());
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
