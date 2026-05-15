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
public class ContextRecallMetric {

    private static final Pattern SCORE_PATTERN = Pattern.compile("分数[：:][\\s]*([0-9.]+)");
    private final AiChatService aiChatService;

    public ContextRecallMetric(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    public double calculate(String question, String groundTruth, List<String> contexts, AiRuntimeContext context, String userId) {
        if (!StringUtils.hasText(groundTruth) || contexts.isEmpty()) {
            return 0.0;
        }

        String contextStr = String.join("\n---\n", contexts);
        String prompt = String.format(RagasPrompts.CONTEXT_RECALL_PROMPT, contextStr, question, groundTruth);

        try {
            // 使用唯一的 conversationId 以确保评测的无状态性
            String conversationId = "eval:context_recall:" + java.util.UUID.randomUUID().toString().substring(0, 8);
            String response = aiChatService.call(
                    AiChatScene.EVALUATION,
                    prompt,
                    context,
                    conversationId,
                    userId);

            return parseScore(response);
        } catch (Exception e) {
            log.error("Context recall metric calculation failed: {}", e.getMessage());
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
