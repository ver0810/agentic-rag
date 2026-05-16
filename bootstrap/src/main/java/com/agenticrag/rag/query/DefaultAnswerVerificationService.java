package com.agenticrag.rag.query;

import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.port.vector.VectorIndexPort;
import com.agenticrag.infra.ai.service.AiChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DefaultAnswerVerificationService implements AnswerVerificationService {

    private static final String EVIDENCE_EVAL_PROMPT = """
            你是一个专业的 RAG 评估员。你的任务是评估检索到的知识片段是否足以回答用户的问题。
            
            用户问题: %s
            
            检索到的知识片段:
            %s
            
            评估要求:
            1. 仅判断这些知识是否提供了回答问题的关键信息。
            2. 输出格式为 JSON: {"sufficient": true/false, "score": 0.0-1.0, "reason": "理由"}
            3. 不要输出任何其他文字。
            """;

    private static final String FAITHFULNESS_PROMPT = """
            你是一个专业的 RAG 评估员。你的任务是评估 AI 生成的回答是否完全忠实于提供的知识片段。
            
            用户问题: %s
            知识片段:
            %s
            
            AI 回答:
            %s
            
            评估要求:
            1. 检查 AI 回答中的每一个事实。
            2. 如果 AI 引入了知识片段中没有的信息（幻觉），则视为不忠实。
            3. 如果回答与知识片段矛盾，则视为不忠实。
            4. 输出格式为 JSON: {"faithful": true/false, "score": 0.0-1.0, "reason": "理由"}
            5. 不要输出任何其他文字。
            """;

    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    public DefaultAnswerVerificationService(@Lazy AiChatService aiChatService, ObjectMapper objectMapper) {
        this.aiChatService = aiChatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvidenceQuality evaluateEvidence(String query, List<? extends VectorIndexPort.SearchResult> results, AiRuntimeContext context) {
        if (results == null || results.isEmpty()) {
            return new EvidenceQuality(false, 0.0, "No results found");
        }

        String contextText = formatContext(results);
        String prompt = String.format(EVIDENCE_EVAL_PROMPT, query, contextText);
        
        try {
            String response = aiChatService.call(AiChatScene.EVALUATION, prompt, context, "eval:evidence:" + Integer.toHexString(query.hashCode()), "system");
            return parseEvidenceResult(response);
        } catch (Exception e) {
            log.error("Failed to evaluate evidence for query '{}': {}", query, e.getMessage(), e);
            return new EvidenceQuality(true, 1.0, "Error during evaluation, defaulting to sufficient");
        }
    }

    @Override
    public FaithfulnessResult verifyFaithfulness(String query, String answer, List<? extends VectorIndexPort.SearchResult> results, AiRuntimeContext context) {
        if (!StringUtils.hasText(answer)) {
            return new FaithfulnessResult(true, 1.0, "Empty answer");
        }

        String contextText = formatContext(results);
        String prompt = String.format(FAITHFULNESS_PROMPT, query, contextText, answer);
        
        try {
            String response = aiChatService.call(AiChatScene.EVALUATION, prompt, context, "eval:faithfulness:" + Integer.toHexString(answer.hashCode()), "system");
            return parseFaithfulnessResult(response);
        } catch (Exception e) {
            log.error("Failed to verify faithfulness: {}", e.getMessage(), e);
            return new FaithfulnessResult(true, 1.0, "Error during verification");
        }
    }

    private String formatContext(List<? extends VectorIndexPort.SearchResult> results) {
        return results.stream()
                .limit(5)
                .map(r -> "- " + r.content())
                .collect(Collectors.joining("\n"));
    }

    private EvidenceQuality parseEvidenceResult(String response) {
        try {
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));
            return new EvidenceQuality(
                    root.path("sufficient").asBoolean(true),
                    root.path("score").asDouble(0.8),
                    root.path("reason").asText("Unknown")
            );
        } catch (Exception e) {
            log.error("Failed to parse evidence result: {}, original response: {}", e.getMessage(), response, e);
            return new EvidenceQuality(true, 0.8, "Parse error");
        }
    }

    private FaithfulnessResult parseFaithfulnessResult(String response) {
        try {
            JsonNode root = objectMapper.readTree(cleanJsonResponse(response));
            return new FaithfulnessResult(
                    root.path("faithful").asBoolean(true),
                    root.path("score").asDouble(0.8),
                    root.path("reason").asText("Unknown")
            );
        } catch (Exception e) {
            log.error("Failed to parse faithfulness result: {}, original response: {}", e.getMessage(), response, e);
            return new FaithfulnessResult(true, 0.8, "Parse error");
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "";
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}
