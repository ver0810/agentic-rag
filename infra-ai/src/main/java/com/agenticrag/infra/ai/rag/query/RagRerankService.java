package com.agenticrag.infra.ai.rag.query;

import com.agenticrag.infra.ai.rag.vector.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RagRerankService {

    public List<VectorStore.VectorSearchResult> rerank(String query,
                                                       List<VectorStore.VectorSearchResult> results,
                                                       int topK) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<String> terms = extractTerms(query);
        return results.stream()
                .map(result -> new RankedResult(result, blendScore(result, terms)))
                .sorted(Comparator.comparing(RankedResult::score).reversed())
                .limit(topK)
                .map(RankedResult::toResult)
                .toList();
    }

    private float blendScore(VectorStore.VectorSearchResult result, List<String> terms) {
        float lexical = lexicalScore(result.content(), terms);
        return result.score() * 0.8f + lexical * 0.2f;
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
            } else if (token.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
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

    private record RankedResult(VectorStore.VectorSearchResult source, float score) {
        private VectorStore.VectorSearchResult toResult() {
            return new VectorStore.VectorSearchResult(source.chunkId(), source.content(), score, source.metadata());
        }
    }
}
