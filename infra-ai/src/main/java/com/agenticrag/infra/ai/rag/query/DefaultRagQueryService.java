package com.agenticrag.infra.ai.rag.query;

import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.rag.vector.VectorStore;
import com.agenticrag.infra.ai.service.AiChatService;
import com.agenticrag.infra.ai.service.KnowledgeEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DefaultRagQueryService implements RagQueryService {

    private final KnowledgeEmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final AiChatService chatService;

    private static final int DEFAULT_TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.7;

    private static final String DEFAULT_PROMPT_TEMPLATE = """
            基于以下上下文回答用户问题。如果上下文中没有相关信息，请说明无法根据提供的信息回答。
            
            上下文：
            %s
            
            用户问题：%s
            
            回答：
            """;

    public DefaultRagQueryService(KnowledgeEmbeddingService embeddingService,
                                   VectorStore vectorStore,
                                   @Lazy AiChatService chatService) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.chatService = chatService;
    }

    @Override
    public String query(String query, String kbId, String userId) {
        return query(query, kbId, userId, null, DEFAULT_TOP_K);
    }

    @Override
    public String query(String query, String kbId, String userId, int topK) {
        return query(query, kbId, userId, null, topK);
    }

    @Override
    public String query(String query, String kbId, String userId, AiRuntimeContext context, int topK) {
        return queryDetailed(query, kbId, userId, context, topK).answer();
    }

    @Override
    public RagQueryResult queryDetailed(String query, String kbId, String userId, int topK) {
        return queryDetailed(query, kbId, userId, null, topK);
    }

    @Override
    public RagQueryResult queryDetailed(String query, String kbId, String userId, AiRuntimeContext context, int topK) {
        log.info("RAG query: kbId={}, query={}", kbId, query);

        float[] queryEmbedding = embeddingService.embed(query);

        List<VectorStore.VectorSearchResult> results = vectorStore.search(queryEmbedding, topK, Map.of("kbId", kbId));

        results = results.stream()
                .filter(r -> r.score() >= SIMILARITY_THRESHOLD)
                .collect(Collectors.toList());

        if (results.isEmpty()) {
            log.info("No relevant context found for query: {}", query);
            return new RagQueryResult(
                    "抱歉，我无法根据现有的知识库内容回答您的问题。请尝试换个问题，或者确认知识库中是否有相关信息。",
                    List.of(),
                    List.of());
        }

        String retrievedContext = results.stream()
                .map(VectorStore.VectorSearchResult::content)
                .collect(Collectors.joining("\n\n"));

        String prompt = String.format(DEFAULT_PROMPT_TEMPLATE, retrievedContext, query);

        log.info("RAG query found {} relevant chunks, generating answer...", results.size());

        String conversationId = "rag:" + kbId + ":" + (userId == null ? "anonymous" : userId);
        String answer = chatService.call(AiChatScene.RAG_QA, prompt, context, conversationId, userId, null);
        return new RagQueryResult(
                answer,
                results.stream().map(this::toCitation).toList(),
                results.stream().map(this::toRetrievedChunk).toList());
    }

    private RagQueryResult.Citation toCitation(VectorStore.VectorSearchResult result) {
        return new RagQueryResult.Citation(
                result.chunkId(),
                metadataValue(result, "docId"),
                metadataValue(result, "docName"),
                metadataIntegerValue(result, "chunkIndex"),
                result.score(),
                abbreviate(result.content(), 200));
    }

    private RagQueryResult.RetrievedChunk toRetrievedChunk(VectorStore.VectorSearchResult result) {
        return new RagQueryResult.RetrievedChunk(
                result.chunkId(),
                metadataValue(result, "docId"),
                metadataValue(result, "docName"),
                metadataIntegerValue(result, "chunkIndex"),
                result.score(),
                result.content());
    }

    private String metadataValue(VectorStore.VectorSearchResult result, String key) {
        Object value = result.metadata().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer metadataIntegerValue(VectorStore.VectorSearchResult result, String key) {
        Object value = result.metadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
