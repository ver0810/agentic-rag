package com.agenticrag.infra.ai.rag.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PgVectorStore implements VectorStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PgVectorStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void store(String chunkId, String content, float[] embedding, Map<String, Object> metadata) {
        String metadataJson = serializeMetadata(metadata);
        String vectorStr = formatVector(embedding);
        
        jdbcTemplate.update(
                "INSERT INTO t_knowledge_vector (id, content, metadata, embedding) VALUES (?, ?::jsonb, ?::vector) " +
                "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding",
                chunkId, content, metadataJson, vectorStr);
    }

    @Override
    public List<VectorSearchResult> search(float[] queryEmbedding, int topK) {
        return search(queryEmbedding, topK, null);
    }

    @Override
    public List<VectorSearchResult> search(float[] queryEmbedding, int topK, Map<String, Object> filter) {
        String vectorStr = formatVector(queryEmbedding);
        
        String sql = "SELECT id, content, 1 - (embedding <=> ?::vector) as score, metadata " +
                     "FROM t_knowledge_vector " +
                     "ORDER BY embedding <=> ?::vector " +
                     "LIMIT ?";
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String id = rs.getString("id");
            String content = rs.getString("content");
            float score = rs.getFloat("score");
            String metadataStr = rs.getString("metadata");
            Map<String, Object> metadata = deserializeMetadata(metadataStr);
            return new VectorSearchResult(id, content, score, metadata);
        }, vectorStr, vectorStr, topK);
    }

    @Override
    public void deleteByDocId(String docId) {
        jdbcTemplate.update(
                "DELETE FROM t_knowledge_vector WHERE metadata->>'docId' = ?", docId);
    }

    @Override
    public void deleteByKbId(String kbId) {
        jdbcTemplate.update(
                "DELETE FROM t_knowledge_vector WHERE metadata->>'kbId' = ?", kbId);
    }

    private String formatVector(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
