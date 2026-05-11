package com.agenticrag.infrastructure.reranker;

import com.agenticrag.infra.ai.port.reranker.RerankerPort;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LexicalRerankerAdapter implements RerankerPort {

    private final float retrievalWeight;
    private final float lexicalWeight;

    public LexicalRerankerAdapter(float retrievalWeight, float lexicalWeight) {
        this.retrievalWeight = retrievalWeight;
        this.lexicalWeight = lexicalWeight;
    }

    @Override
    public List<RerankResult> rerank(String query, List<RerankCandidate> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<String> terms = extractTerms(query);
        return candidates.stream()
                .map(candidate -> {
                    float blended = candidate.originalScore() * retrievalWeight
                            + lexicalScore(candidate.content(), terms) * lexicalWeight;
                    return new RerankResult(candidate.chunkId(), candidate.content(), blended, candidate.metadata());
                })
                .sorted(Comparator.comparing(RerankResult::score).reversed())
                .limit(topK)
                .toList();
    }

    private float lexicalScore(String content, List<String> terms) {
        if (!StringUtils.hasText(content) || terms.isEmpty()) {
            return 0f;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String term : terms) {
            if (lower.contains(term.toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return (float) hits / terms.size();
    }

    private List<String> extractTerms(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String normalized = query.replaceAll("[^\\p{IsHan}\\p{IsAlphabetic}\\p{IsDigit}]+", " ").trim();
        Set<String> terms = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (token.codePointCount(0, token.length()) <= 2) {
                terms.add(token);
            } else if (token.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
                for (int i = 0; i < token.length() - 1; i++) {
                    terms.add(token.substring(i, i + 2));
                }
            } else {
                terms.add(token);
            }
            if (terms.size() >= 10) {
                break;
            }
        }
        return new ArrayList<>(terms);
    }
}
