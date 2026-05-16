package com.agenticrag.rag.query;

import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.service.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RagQueryRewriteService {

    private static final String REWRITE_PROMPT = """
            你负责把用户问题改写成适合向量数据库检索的查询语句。

            改写规则：
            1. 如果问题是"这个文档/简历/报告是什么"，改写为文档中可能存在的关键词，如人名、标题、主题。
            2. 如果问题是"总结/概括"，改写为文档的核心主题词。
            3. 如果问题是代词（如"他/她/它"），保留上下文但补充可能的实体词。
            4. 保留专有名词、实体、时间。
            5. 只输出改写后的查询，不要解释。

            示例：
            - "这是谁的简历？" → "简历 姓名 个人信息"
            - "总结一下" → "主要内容 概述"
            - "他有什么技能？" → "技能 能力 专业"

            用户问题：%s
            改写结果：""";

    private static final String MULTI_REWRITE_PROMPT = """
            你负责把用户问题从不同角度改写成 3 个适合检索的查询。
            每个查询用不同的表达方式，覆盖不同的关键词。

            示例输出格式（每行一个查询）：
            关键词1 关键词2
            关键词3 关键词4
            关键词5 关键词6

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
        } catch (Exception e) {
            log.warn("Query rewrite failed, falling back to original: {}", e.getMessage());
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
                    String.format(MULTI_REWRITE_PROMPT, query),
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
        } catch (Exception e) {
            log.warn("Multi-query rewrite failed, falling back to original: {}", e.getMessage());
            return List.of(query);
        }
    }
}
