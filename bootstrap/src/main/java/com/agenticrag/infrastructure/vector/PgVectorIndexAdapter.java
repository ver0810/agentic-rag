package com.agenticrag.infrastructure.vector;

import com.agenticrag.infra.ai.port.vector.VectorIndexPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PgVectorIndexAdapter implements VectorIndexPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PgVectorIndexAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void store(String chunkId, String content, float[] embedding, Map<String, Object> metadata) {
        String metadataJson = serializeMetadata(metadata);
        String vectorStr = formatVector(embedding);

        jdbcTemplate.update(
                "INSERT INTO t_knowledge_vector (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?::vector) " +
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

        StringBuilder sql = new StringBuilder(
                "SELECT id, content, 1 - (embedding <=> ?::vector) as score, metadata " +
                        "FROM t_knowledge_vector");
        Object[] args;
        if (filter != null && !filter.isEmpty()) {
            sql.append(" WHERE metadata @> ?::jsonb");
            sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
            args = new Object[]{vectorStr, serializeMetadata(filter), vectorStr, topK};
        } else {
            sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
            args = new Object[]{vectorStr, vectorStr, topK};
        }

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String id = rs.getString("id");
            String content = rs.getString("content");
            float score = rs.getFloat("score");
            String metadataStr = rs.getString("metadata");
            Map<String, Object> metadata = deserializeMetadata(metadataStr);
            return new VectorSearchResult(id, content, score, metadata);
        }, args);
    }

    @Override
    public List<VectorSearchResult> keywordSearch(String query, int topK, Map<String, Object> filter) {
        List<String> terms = extractSearchTerms(query);
        if (terms.isEmpty() || topK <= 0) {
            return List.of();
        }

        StringBuilder scoreExpr = new StringBuilder();
        StringBuilder whereExpr = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) {
                scoreExpr.append(" + ");
                whereExpr.append(" OR ");
            }
            scoreExpr.append("CASE WHEN content ILIKE ? THEN 1 ELSE 0 END");
            whereExpr.append("content ILIKE ?");
            String pattern = "%" + terms.get(i) + "%";
            args.add(pattern);
        }

        StringBuilder sql = new StringBuilder(
                "SELECT id, content, (" + scoreExpr + ")::float / ? as score, metadata " +
                        "FROM t_knowledge_vector WHERE ");

        if (filter != null && !filter.isEmpty()) {
            sql.append("metadata @> ?::jsonb AND (").append(whereExpr).append(")");
            args.add((float) terms.size());
            args.add(serializeMetadata(filter));
        } else {
            sql.append("(").append(whereExpr).append(")");
            args.add((float) terms.size());
        }
        for (String term : terms) {
            args.add("%" + term + "%");
        }
        sql.append(" ORDER BY score DESC, id ASC LIMIT ?");
        args.add(topK);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new VectorSearchResult(
                rs.getString("id"),
                rs.getString("content"),
                rs.getFloat("score"),
                deserializeMetadata(rs.getString("metadata"))
        ), args.toArray());
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

    private List<String> extractSearchTerms(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String normalized = query.replaceAll("[^\\p{IsHan}\\p{IsAlphabetic}\\p{IsDigit}]+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (token.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
                if (token.length() <= 2) {
                    terms.add(token);
                } else {
                    for (int i = 0; i < token.length() - 1; i++) {
                        terms.add(token.substring(i, i + 2));
                    }
                }
            } else if (token.length() >= 2) {
                terms.add(token.toLowerCase());
            }
            if (terms.size() >= 8) {
                break;
            }
        }
        return new ArrayList<>(terms);
    }

    private record VectorSearchResult(String chunkId, String content, float score, Map<String, Object> metadata)
            implements SearchResult {}
}
