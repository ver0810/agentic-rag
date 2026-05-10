package com.agenticrag.infra.ai.rag.query;

import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.config.RagProperties;
import com.agenticrag.infra.ai.rag.vector.VectorStore;
import com.agenticrag.infra.ai.service.AiChatService;
import com.agenticrag.infra.ai.service.KnowledgeEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DefaultRagQueryService implements RagQueryService {

    private final KnowledgeEmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final AiChatService chatService;
    private final RagProperties ragProperties;
    private final RagQueryRewriteService ragQueryRewriteService;
    private final RagRerankService ragRerankService;
    private final RagTraceRecorder ragTraceRecorder;

    private static final String DEFAULT_PROMPT_TEMPLATE = """
            你是一个严格基于证据回答问题的知识库助手。
            请只依据以下证据回答用户问题；如果证据不足，请明确说明无法确认。
            回答时尽量在相关结论后标注证据编号，如 [1]、[2]。
            
            证据：
            %s
            
            用户问题：%s
            
            回答：
            """;

    public DefaultRagQueryService(KnowledgeEmbeddingService embeddingService,
                                   VectorStore vectorStore,
                                   @Lazy AiChatService chatService,
                                   RagProperties ragProperties,
                                   RagQueryRewriteService ragQueryRewriteService,
                                   RagRerankService ragRerankService,
                                   @Lazy RagTraceRecorder ragTraceRecorder) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.chatService = chatService;
        this.ragProperties = ragProperties;
        this.ragQueryRewriteService = ragQueryRewriteService;
        this.ragRerankService = ragRerankService;
        this.ragTraceRecorder = ragTraceRecorder;
    }

    @Override
    public String query(String query, String kbId, String userId) {
        return query(query, kbId, userId, null, ragProperties.getDefaultTopK());
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
        int effectiveTopK = topK > 0 ? topK : ragProperties.getDefaultTopK();
        String traceId = ragTraceRecorder.startRun(
                "rag_query",
                "RagQueryService.queryDetailed",
                "rag:" + kbId + ":" + (userId == null ? "anonymous" : userId),
                userId,
                Map.of("kbId", kbId, "query", query, "topK", effectiveTopK));
        try {
            String rewrittenQuery = query;
            if (ragProperties.isRewriteEnabled()) {
                String rewriteNodeId = ragTraceRecorder.startNode(traceId, "rewrite", "query_rewrite", Map.of("query", query));
                try {
                    rewrittenQuery = ragQueryRewriteService.rewrite(query, userId, context);
                    ragTraceRecorder.completeNode(traceId, rewriteNodeId, Map.of("rewrittenQuery", rewrittenQuery));
                } catch (Exception ex) {
                    ragTraceRecorder.failNode(traceId, rewriteNodeId, ex.getMessage(), Map.of());
                    throw ex;
                }
            }

            String retrieveNodeId = ragTraceRecorder.startNode(traceId, "retrieve", "hybrid_retrieve", Map.of("rewrittenQuery", rewrittenQuery));
            float[] queryEmbedding;
            List<VectorStore.VectorSearchResult> results;
            try {
                queryEmbedding = embeddingService.embed(rewrittenQuery);
                Map<String, Object> filter = Map.of("kbId", kbId);
                List<VectorStore.VectorSearchResult> vectorResults = vectorStore.search(
                        queryEmbedding,
                        Math.max(effectiveTopK, ragProperties.getVectorTopK()),
                        filter);
                results = vectorResults;
                int keywordCount = 0;
                if (ragProperties.isHybridEnabled()) {
                    List<VectorStore.VectorSearchResult> keywordResults = vectorStore.keywordSearch(
                            rewrittenQuery,
                            Math.max(effectiveTopK, ragProperties.getKeywordTopK()),
                            filter);
                    keywordCount = keywordResults.size();
                    results = mergeResults(vectorResults, keywordResults, effectiveTopK);
                } else {
                    results = vectorResults.stream()
                            .sorted(Comparator.comparing(VectorStore.VectorSearchResult::score).reversed())
                            .limit(effectiveTopK)
                            .toList();
                }
                results = results.stream()
                        .filter(r -> r.score() >= ragProperties.getSimilarityThreshold())
                        .collect(Collectors.toList());
                ragTraceRecorder.completeNode(traceId, retrieveNodeId, Map.of(
                        "vectorResultCount", vectorResults.size(),
                        "keywordResultCount", keywordCount,
                        "filteredResultCount", results.size()));
            } catch (Exception ex) {
                ragTraceRecorder.failNode(traceId, retrieveNodeId, ex.getMessage(), Map.of());
                throw ex;
            }

            if (ragProperties.isRerankEnabled()) {
                String rerankNodeId = ragTraceRecorder.startNode(traceId, "rerank", "lexical_rerank", Map.of("candidateCount", results.size()));
                try {
                    results = ragRerankService.rerank(rewrittenQuery, results, effectiveTopK);
                    ragTraceRecorder.completeNode(traceId, rerankNodeId, Map.of("rerankedCount", results.size()));
                } catch (Exception ex) {
                    ragTraceRecorder.failNode(traceId, rerankNodeId, ex.getMessage(), Map.of());
                    throw ex;
                }
            } else {
                results = results.stream().limit(effectiveTopK).toList();
            }

            if (results.isEmpty()) {
                log.info("No relevant context found for query: {}", query);
                RagQueryResult emptyResult = new RagQueryResult(
                        "抱歉，我无法根据现有的知识库内容回答您的问题。请尝试换个问题，或者确认知识库中是否有相关信息。",
                        traceId,
                        rewrittenQuery,
                        List.of(),
                        List.of());
                ragTraceRecorder.completeRun(traceId, Map.of("rewrittenQuery", rewrittenQuery, "answerState", "empty"));
                return emptyResult;
            }

            String retrievedContext = results.stream()
                    .limit(ragProperties.getMaxContextChunks())
                    .map(this::toEvidenceBlock)
                    .collect(Collectors.joining("\n\n"));

            String prompt = String.format(DEFAULT_PROMPT_TEMPLATE, retrievedContext, query);

            log.info("RAG query found {} relevant chunks, generating answer...", results.size());

            String generateNodeId = ragTraceRecorder.startNode(traceId, "generate", "answer_generation", Map.of("contextChunkCount", Math.min(results.size(), ragProperties.getMaxContextChunks())));
            String answer;
            try {
                String conversationId = "rag:" + kbId + ":" + (userId == null ? "anonymous" : userId);
                answer = chatService.call(AiChatScene.RAG_QA, prompt, context, conversationId, userId, null);
                ragTraceRecorder.completeNode(traceId, generateNodeId, Map.of("answerLength", answer.length()));
            } catch (Exception ex) {
                ragTraceRecorder.failNode(traceId, generateNodeId, ex.getMessage(), Map.of());
                throw ex;
            }

            RagQueryResult result = new RagQueryResult(
                    answer,
                    traceId,
                    rewrittenQuery,
                    results.stream().map(this::toCitation).toList(),
                    results.stream().map(this::toRetrievedChunk).toList());
            ragTraceRecorder.completeRun(traceId, Map.of(
                    "rewrittenQuery", rewrittenQuery,
                    "citationCount", result.citations().size(),
                    "retrievedChunkCount", result.retrievedChunks().size()));
            return result;
        } catch (Exception ex) {
            ragTraceRecorder.failRun(traceId, ex.getMessage(), Map.of("kbId", kbId));
            throw ex;
        }
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

    private String toEvidenceBlock(VectorStore.VectorSearchResult result) {
        Integer chunkIndex = metadataIntegerValue(result, "chunkIndex");
        String docName = metadataValue(result, "docName");
        String label = chunkIndex == null ? result.chunkId() : String.valueOf(chunkIndex + 1);
        return "[" + label + "] "
                + (docName == null ? "Unknown Document" : docName)
                + "\n"
                + result.content();
    }

    private List<VectorStore.VectorSearchResult> mergeResults(List<VectorStore.VectorSearchResult> vectorResults,
                                                              List<VectorStore.VectorSearchResult> keywordResults,
                                                              int topK) {
        Map<String, ScoredResult> merged = new LinkedHashMap<>();
        for (VectorStore.VectorSearchResult result : vectorResults) {
            merged.put(result.chunkId(), new ScoredResult(result, result.score() * (float) ragProperties.getVectorWeight()));
        }
        for (VectorStore.VectorSearchResult result : keywordResults) {
            float keywordScore = result.score() * (float) ragProperties.getKeywordWeight();
            ScoredResult existing = merged.get(result.chunkId());
            if (existing == null) {
                merged.put(result.chunkId(), new ScoredResult(result, keywordScore));
            } else {
                existing.score += keywordScore;
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparing((ScoredResult item) -> item.score).reversed())
                .limit(topK)
                .map(ScoredResult::toResult)
                .toList();
    }

    private static class ScoredResult {
        private final VectorStore.VectorSearchResult source;
        private float score;

        private ScoredResult(VectorStore.VectorSearchResult source, float score) {
            this.source = source;
            this.score = score;
        }

        private VectorStore.VectorSearchResult toResult() {
            return new VectorStore.VectorSearchResult(source.chunkId(), source.content(), score, source.metadata());
        }
    }
}
