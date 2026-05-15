package com.agenticrag.rag.eval.ragas.metrics;

import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.service.AiChatService;
import com.agenticrag.rag.eval.ragas.prompt.RagasPrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ContextPrecisionMetric {

    private static final Pattern SCORE_PATTERN = Pattern.compile("分数[：:][\\s]*([0-9.]+)");
    private static final String CONVERSATION_ID = "ragas:context_precision";

    private final AiChatService aiChatService;

    public ContextPrecisionMetric(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    public double calculate(String question, String groundTruth, List<String> contexts, AiRuntimeContext context, String userId) {
        if (contexts.isEmpty()) {
            return 0.0;
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            contextBuilder.append("[").append(i + 1).append("] ").append(contexts.get(i)).append("\n\n");
        }

        String prompt = String.format(RagasPrompts.CONTEXT_PRECISION_PROMPT,
                contextBuilder.toString(), question, groundTruth);

        try {
            String response = aiChatService.call(
                    AiChatScene.EVALUATION,
                    prompt,
                    context,
                    CONVERSATION_ID,
                    userId);

            return parseScore(response);
        } catch (Exception e) {
            log.error("Context precision metric calculation failed: {}", e.getMessage());
            return -1.0;
        }
    }

    private double parseScore(String response) {
        if (!StringUtils.hasText(response)) {
            return -1.0;
        }
        Matcher matcher = SCORE_PATTERN.matcher(response);
        if (matcher.find()) {
            try {
                double score = Double.parseDouble(matcher.group(1));
                return Math.max(0.0, Math.min(1.0, score));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1.0;
    }
}
