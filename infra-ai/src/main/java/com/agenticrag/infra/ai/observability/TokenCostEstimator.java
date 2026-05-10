package com.agenticrag.infra.ai.observability;

import com.agenticrag.infra.ai.config.AiObservabilityProperties;
import org.springframework.stereotype.Component;

@Component
public class TokenCostEstimator {

    private final AiObservabilityProperties properties;

    public TokenCostEstimator(AiObservabilityProperties properties) {
        this.properties = properties;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int charCount = text.codePointCount(0, text.length());
        return estimateTokensByCharCount(charCount);
    }

    public int estimateTokensByCharCount(int charCount) {
        if (charCount <= 0) {
            return 0;
        }
        double charsPerToken = Math.max(1.0d, properties.getCharsPerToken());
        return (int) Math.ceil(charCount / charsPerToken);
    }

    public double estimateChatInputCost(int tokens) {
        return estimateChatInputCost((long) tokens);
    }

    public double estimateChatOutputCost(int tokens) {
        return estimateChatOutputCost((long) tokens);
    }

    public double estimateEmbeddingCost(int tokens) {
        return estimateEmbeddingCost((long) tokens);
    }

    public double estimateChatInputCost(long tokens) {
        return round6(tokens * properties.getCost().getChatInputPer1kTokens() / 1000d);
    }

    public double estimateChatOutputCost(long tokens) {
        return round6(tokens * properties.getCost().getChatOutputPer1kTokens() / 1000d);
    }

    public double estimateEmbeddingCost(long tokens) {
        return round6(tokens * properties.getCost().getEmbeddingPer1kTokens() / 1000d);
    }

    public double round6(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
