package com.agenticrag.rag.query;

import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.service.AiChatService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagQueryRewriteService {

    private static final String REWRITE_PROMPT = """
            你负责把用户问题改写成适合知识库检索的独立查询。
            规则：
            1. 保留原问题中的专有名词、实体、时间和限定条件。
            2. 不要发散，不要补充文档中不存在的事实。
            3. 只输出一行改写后的查询，不要解释，不要加引号。

            用户问题：%s
            """;

    private static final String MULTI_REWRITE_PROMPT = """
            你负责把用户问题从 %d 个不同角度改写成适合知识库检索的查询。
            规则：
            1. 每个查询用不同的表达方式，但都指向同一个核心问题。
            2. 保留原问题中的专有名词、实体、时间和限定条件。
            3. 不要发散，不要补充文档中不存在的事实。
            4. 每行一个查询，不要编号，不要解释，不要加引号。

            用户问题：%s
            """;

    private final AiChatService aiChatService;

    public RagQueryRewriteService(@Lazy AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    public String rewrite(String query, String userId, AiRuntimeContext context) {
        if (!StringUtils.hasText(query)) {
            return query;
        }
        try {
            String rewritten = aiChatService.call(
                    AiChatScene.GENERAL,
                    String.format(REWRITE_PROMPT, query),
                    context,
                    "rag:rewrite:" + Integer.toHexString(query.hashCode()),
                    userId);
            if (!StringUtils.hasText(rewritten)) {
                return query;
            }
            String normalized = rewritten.trim().replace("\n", " ");
            return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
        } catch (Exception ignored) {
            return query;
        }
    }

    public List<String> rewriteMultiple(String query, int count, String userId, AiRuntimeContext context) {
        if (!StringUtils.hasText(query) || count <= 1) {
            return List.of(query);
        }
        try {
            String result = aiChatService.call(
                    AiChatScene.GENERAL,
                    String.format(MULTI_REWRITE_PROMPT, count, query),
                    context,
                    "rag:multi-rewrite:" + Integer.toHexString(query.hashCode()),
                    userId);
            if (!StringUtils.hasText(result)) {
                return List.of(query);
            }
            List<String> queries = new ArrayList<>();
            queries.add(query);
            for (String line : result.split("\n")) {
                String normalized = line.trim().replace("\n", " ");
                if (normalized.length() > 200) {
                    normalized = normalized.substring(0, 200);
                }
                if (StringUtils.hasText(normalized) && !normalized.equals(query)) {
                    queries.add(normalized);
                }
                if (queries.size() >= count) {
                    break;
                }
            }
            return queries;
        } catch (Exception ignored) {
            return List.of(query);
        }
    }
}
