package com.agenticrag.rag.query;

import com.agenticrag.infra.ai.config.RagProperties;
import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.observability.TokenCostEstimator;
import com.agenticrag.infra.ai.port.embedding.KnowledgeEmbeddingPort;
import com.agenticrag.infra.ai.port.vector.VectorIndexPort;
import com.agenticrag.infra.ai.service.AiChatService;
import com.agenticrag.knowledge.dao.entity.KnowledgeBaseEntity;
import com.agenticrag.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DefaultRagQueryService implements RagQueryService {

    private static final String DEFAULT_PROMPT_TEMPLATE = """
            你是一个严格基于证据回答问题的知识库助手。
            请只依据以下证据回答用户问题；如果证据不足，请明确说明无法确认。
            回答时尽量在相关结论后标注证据编号，如 [1]、[2]。
            
            证据：
            %s
            
            用户问题：%s
            
            回答：
            """;

    private final KnowledgeEmbeddingPort embeddingPort;
    private final VectorIndexPort vectorIndexPort;
    private final AiChatService chatService;
    private final RagProperties ragProperties;
    private final RagQueryRewriteService ragQueryRewriteService;
    private final RagRerankService ragRerankService;
    private final RagTraceRecorder ragTraceRecorder;
    private final TokenCostEstimator tokenCostEstimator;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final RagCacheService ragCacheService;

    public DefaultRagQueryService(KnowledgeEmbeddingPort embeddingPort,
                                  VectorIndexPort vectorIndexPort,
                                  @Lazy AiChatService chatService,
                                  RagProperties ragProperties,
                                  RagQueryRewriteService ragQueryRewriteService,
                                  RagRerankService ragRerankService,
                                  @Lazy RagTraceRecorder ragTraceRecorder,
                                  TokenCostEstimator tokenCostEstimator,
                                  KnowledgeBaseMapper knowledgeBaseMapper,
                                  RagCacheService ragCacheService) {
        this.embeddingPort = embeddingPort;
        this.vectorIndexPort = vectorIndexPort;
        this.chatService = chatService;
        this.ragProperties = ragProperties;
        this.ragQueryRewriteService = ragQueryRewriteService;
        this.ragRerankService = ragRerankService;
        this.ragTraceRecorder = ragTraceRecorder;
        this.tokenCostEstimator = tokenCostEstimator;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.ragCacheService = ragCacheService;
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
        KnowledgeBaseSettings kbSettings = resolveKnowledgeBaseSettings(kbId);
        int effectiveTopK = topK > 0 ? topK : ragProperties.getDefaultTopK();
        double effectiveThreshold = kbSettings.similarityThreshold();
        String traceId = ragTraceRecorder.startRun(
                "rag_query",
                "RagQueryService.queryDetailed",
                "rag:" + kbId + ":" + (userId == null ? "anonymous" : userId),
                userId,
                Map.of("kbId", kbId, "query", query, "topK", effectiveTopK));
        try {
            List<String> queries;
            if (ragProperties.isRewriteEnabled() && ragProperties.isMultiQueryEnabled()) {
                String rewriteNodeId = ragTraceRecorder.startNode(traceId, "rewrite", "multi_query_rewrite", Map.of("query", query));
                try {
                    queries = ragQueryRewriteService.rewriteMultiple(query, ragProperties.getMultiQueryCount(), userId, context);
                    ragTraceRecorder.completeNode(traceId, rewriteNodeId, Map.of("queryCount", queries.size(), "queries", queries));
                } catch (Exception ex) {
                    ragTraceRecorder.failNode(traceId, rewriteNodeId, ex.getMessage(), Map.of());
                    queries = List.of(query);
                }
            } else if (ragProperties.isRewriteEnabled()) {
                String rewriteNodeId = ragTraceRecorder.startNode(traceId, "rewrite", "query_rewrite", Map.of("query", query));
                try {
                    String rewrittenQuery = ragQueryRewriteService.rewrite(query, userId, context);
                    queries = List.of(rewrittenQuery);
                    ragTraceRecorder.completeNode(traceId, rewriteNodeId, Map.of("rewrittenQuery", rewrittenQuery));
                } catch (Exception ex) {
                    ragTraceRecorder.failNode(traceId, rewriteNodeId, ex.getMessage(), Map.of());
                    queries = List.of(query);
                }
            } else {
                queries = List.of(query);
            }

            String retrieveNodeId = ragTraceRecorder.startNode(traceId, "retrieve", "hybrid_retrieve", Map.of("queries", queries));
            List<? extends VectorIndexPort.SearchResult> results;
            int totalEstimatedEmbeddingTokens = 0;
            double totalEstimatedEmbeddingCost = 0;
            try {
                Map<String, Object> filter = Map.of("kbId", kbId);
                List<List<? extends VectorIndexPort.SearchResult>> allVectorResults = new ArrayList<>();
                List<List<? extends VectorIndexPort.SearchResult>> allKeywordResults = new ArrayList<>();

                for (String q : queries) {
                    int estimatedTokens = tokenCostEstimator.estimateTokens(q);
                    totalEstimatedEmbeddingTokens += estimatedTokens;
                    totalEstimatedEmbeddingCost += tokenCostEstimator.estimateEmbeddingCost(estimatedTokens);

                    float[] queryEmbedding = ragCacheService.getEmbedding(q);
                    if (queryEmbedding == null) {
                        queryEmbedding = embeddingPort.embed(q);
                        ragCacheService.putEmbedding(q, queryEmbedding);
                    }

                    String resultCacheKey = RagCacheService.buildResultCacheKey(q, kbId, effectiveTopK);
                    List<? extends VectorIndexPort.SearchResult> cachedResults = ragCacheService.getResults(resultCacheKey);
                    if (cachedResults != null) {
                        allVectorResults.add(cachedResults);
                    } else {
                        List<? extends VectorIndexPort.SearchResult> vectorResults = vectorIndexPort.search(
                                queryEmbedding,
                                Math.max(effectiveTopK, kbSettings.vectorTopK()),
                                filter)
                                .stream()
                                .filter(r -> r.score() >= effectiveThreshold)
                                .toList();
                        ragCacheService.putResults(resultCacheKey, vectorResults);
                        allVectorResults.add(vectorResults);
                    }

                    if (ragProperties.isHybridEnabled()) {
                        List<? extends VectorIndexPort.SearchResult> keywordResults = vectorIndexPort.keywordSearch(
                                q,
                                Math.max(effectiveTopK, kbSettings.keywordTopK()),
                                filter)
                                .stream()
                                .filter(r -> r.score() >= effectiveThreshold)
                                .toList();
                        allKeywordResults.add(keywordResults);
                    }
                }

                if (queries.size() == 1) {
                    List<? extends VectorIndexPort.SearchResult> vectorResults = allVectorResults.get(0);
                    results = vectorResults;
                    if (ragProperties.isHybridEnabled() && !allKeywordResults.isEmpty()) {
                        results = mergeResults(vectorResults, allKeywordResults.get(0), effectiveTopK);
                    } else {
                        results = vectorResults.stream()
                                .sorted(Comparator.comparing(VectorIndexPort.SearchResult::score).reversed())
                                .limit(effectiveTopK)
                                .toList();
                    }
                } else {
                    results = mergeMultiQueryResults(allVectorResults, allKeywordResults, effectiveTopK);
                }

                int totalVectorCount = allVectorResults.stream().mapToInt(List::size).sum();
                int totalKeywordCount = allKeywordResults.stream().mapToInt(List::size).sum();
                ragTraceRecorder.completeNode(traceId, retrieveNodeId, Map.of(
                        "queryCount", queries.size(),
                        "vectorResultCount", totalVectorCount,
                        "keywordResultCount", totalKeywordCount,
                        "filteredResultCount", results.size(),
                        "estimatedEmbeddingTokens", totalEstimatedEmbeddingTokens,
                        "estimatedEmbeddingCost", totalEstimatedEmbeddingCost));
            } catch (Exception ex) {
                ragTraceRecorder.failNode(traceId, retrieveNodeId, ex.getMessage(), Map.of());
                throw ex;
            }

            if (ragProperties.isRerankEnabled()) {
                String rerankNodeId = ragTraceRecorder.startNode(traceId, "rerank", "lexical_rerank", Map.of("candidateCount", results.size()));
                try {
                    results = ragRerankService.rerank(query, results, effectiveTopK);
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
                        queries.get(0),
                        List.of(),
                        List.of());
                ragTraceRecorder.completeRun(traceId, Map.of(
                        "queries", queries,
                        "answerState", "empty_retrieval",
                        "estimatedEmbeddingTokens", totalEstimatedEmbeddingTokens,
                        "estimatedEmbeddingCost", totalEstimatedEmbeddingCost,
                        "estimatedTotalTokens", totalEstimatedEmbeddingTokens,
                        "estimatedTotalCost", totalEstimatedEmbeddingCost));
                return emptyResult;
            }

            String retrievedContext = results.stream()
                    .limit(ragProperties.getMaxContextChunks())
                    .map(this::toEvidenceBlock)
                    .collect(Collectors.joining("\n\n"));

            String promptTemplate = kbSettings.promptTemplate();
            String prompt = String.format(promptTemplate, retrievedContext, query);
            int estimatedPromptTokens = tokenCostEstimator.estimateTokens(prompt);

            log.info("RAG query found {} relevant chunks, generating answer...", results.size());

            String generateNodeId = ragTraceRecorder.startNode(traceId, "generate", "answer_generation", Map.of("contextChunkCount", Math.min(results.size(), ragProperties.getMaxContextChunks())));
            String answer;
            try {
                String conversationId = "rag:" + kbId + ":" + (userId == null ? "anonymous" : userId);
                answer = chatService.call(AiChatScene.RAG_QA, prompt, context, conversationId, userId);
                int estimatedAnswerTokens = tokenCostEstimator.estimateTokens(answer);
                ragTraceRecorder.completeNode(traceId, generateNodeId, Map.of(
                        "promptLength", prompt.length(),
                        "answerLength", answer.length(),
                        "estimatedChatInputTokens", estimatedPromptTokens,
                        "estimatedChatOutputTokens", estimatedAnswerTokens,
                        "estimatedChatInputCost", tokenCostEstimator.estimateChatInputCost(estimatedPromptTokens),
                        "estimatedChatOutputCost", tokenCostEstimator.estimateChatOutputCost(estimatedAnswerTokens)));
            } catch (Exception ex) {
                ragTraceRecorder.failNode(traceId, generateNodeId, ex.getMessage(), Map.of());
                throw ex;
            }

            int estimatedAnswerTokens = tokenCostEstimator.estimateTokens(answer);
            double estimatedChatInputCost = tokenCostEstimator.estimateChatInputCost(estimatedPromptTokens);
            double estimatedChatOutputCost = tokenCostEstimator.estimateChatOutputCost(estimatedAnswerTokens);
            double estimatedTotalCost = tokenCostEstimator.round6(totalEstimatedEmbeddingCost + estimatedChatInputCost + estimatedChatOutputCost);
            String answerState = determineAnswerState(answer);

            RagQueryResult result = new RagQueryResult(
                    answer,
                    traceId,
                    queries.get(0),
                    results.stream().map(this::toCitation).toList(),
                    results.stream().map(this::toRetrievedChunk).toList());
            Map<String, Object> runExtraData = new LinkedHashMap<>();
            runExtraData.put("queries", queries);
            runExtraData.put("answerState", answerState);
            runExtraData.put("citationCount", result.citations().size());
            runExtraData.put("retrievedChunkCount", result.retrievedChunks().size());
            runExtraData.put("estimatedEmbeddingTokens", totalEstimatedEmbeddingTokens);
            runExtraData.put("estimatedEmbeddingCost", totalEstimatedEmbeddingCost);
            runExtraData.put("estimatedChatInputTokens", estimatedPromptTokens);
            runExtraData.put("estimatedChatOutputTokens", estimatedAnswerTokens);
            runExtraData.put("estimatedChatInputCost", estimatedChatInputCost);
            runExtraData.put("estimatedChatOutputCost", estimatedChatOutputCost);
            runExtraData.put("estimatedTotalTokens", totalEstimatedEmbeddingTokens + estimatedPromptTokens + estimatedAnswerTokens);
            runExtraData.put("estimatedTotalCost", estimatedTotalCost);
            ragTraceRecorder.completeRun(traceId, runExtraData);
            return result;
        } catch (Exception ex) {
            ragTraceRecorder.failRun(traceId, ex.getMessage(), Map.of("kbId", kbId, "answerState", "error"));
            throw ex;
        }
    }

    private KnowledgeBaseSettings resolveKnowledgeBaseSettings(String kbId) {
        if (kbId == null) {
            return new KnowledgeBaseSettings(
                    ragProperties.getSimilarityThreshold(),
                    ragProperties.getVectorTopK(),
                    ragProperties.getKeywordTopK(),
                    defaultPromptTemplate());
        }
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getId, kbId)
                        .eq(KnowledgeBaseEntity::getDeleted, 0)
                        .select(
                                KnowledgeBaseEntity::getSimilarityThreshold,
                                KnowledgeBaseEntity::getVectorTopK,
                                KnowledgeBaseEntity::getKeywordTopK,
                                KnowledgeBaseEntity::getPromptTemplate)
                        .last("limit 1"));
        return new KnowledgeBaseSettings(
                kb != null && kb.getSimilarityThreshold() != null
                        ? kb.getSimilarityThreshold()
                        : ragProperties.getSimilarityThreshold(),
                kb != null && kb.getVectorTopK() != null && kb.getVectorTopK() > 0
                        ? kb.getVectorTopK()
                        : ragProperties.getVectorTopK(),
                kb != null && kb.getKeywordTopK() != null && kb.getKeywordTopK() > 0
                        ? kb.getKeywordTopK()
                        : ragProperties.getKeywordTopK(),
                kb != null && StringUtils.hasText(kb.getPromptTemplate())
                        ? kb.getPromptTemplate()
                        : defaultPromptTemplate());
    }

    private String defaultPromptTemplate() {
        if (StringUtils.hasText(ragProperties.getPromptTemplate())) {
            return ragProperties.getPromptTemplate();
        }
        return DEFAULT_PROMPT_TEMPLATE;
    }

    private String determineAnswerState(String answer) {
        if (!StringUtils.hasText(answer)) {
            return "empty_answer";
        }
        String normalized = answer.replaceAll("\\s+", "");
        List<String> refusalMarkers = List.of(
                "无法根据现有知识库",
                "无法根据提供的证据",
                "证据不足",
                "无法确认",
                "未找到相关信息",
                "没有足够信息");
        for (String marker : refusalMarkers) {
            if (normalized.contains(marker)) {
                return "refused";
            }
        }
        return "answered";
    }

    private RagQueryResult.Citation toCitation(VectorIndexPort.SearchResult result) {
        return new RagQueryResult.Citation(
                result.chunkId(),
                metadataValue(result, "docId"),
                metadataValue(result, "docName"),
                metadataIntegerValue(result, "chunkIndex"),
                result.score(),
                abbreviate(result.content(), 200));
    }

    private RagQueryResult.RetrievedChunk toRetrievedChunk(VectorIndexPort.SearchResult result) {
        return new RagQueryResult.RetrievedChunk(
                result.chunkId(),
                metadataValue(result, "docId"),
                metadataValue(result, "docName"),
                metadataIntegerValue(result, "chunkIndex"),
                result.score(),
                result.content());
    }

    private String metadataValue(VectorIndexPort.SearchResult result, String key) {
        Object value = result.metadata().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer metadataIntegerValue(VectorIndexPort.SearchResult result, String key) {
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

    private String toEvidenceBlock(VectorIndexPort.SearchResult result) {
        Integer chunkIndex = metadataIntegerValue(result, "chunkIndex");
        String docName = metadataValue(result, "docName");
        String label = chunkIndex == null ? result.chunkId() : String.valueOf(chunkIndex + 1);
        return "[" + label + "] "
                + (docName == null ? "Unknown Document" : docName)
                + "\n"
                + result.content();
    }

    private List<SearchResultView> mergeResults(List<? extends VectorIndexPort.SearchResult> vectorResults,
                                                List<? extends VectorIndexPort.SearchResult> keywordResults,
                                                int topK) {
        int k = ragProperties.getRrfK();
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, VectorIndexPort.SearchResult> resultLookup = new LinkedHashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            VectorIndexPort.SearchResult result = vectorResults.get(i);
            double rrfScore = 1.0 / (k + i + 1);
            rrfScores.merge(result.chunkId(), rrfScore, Double::sum);
            resultLookup.putIfAbsent(result.chunkId(), result);
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            VectorIndexPort.SearchResult result = keywordResults.get(i);
            double rrfScore = 1.0 / (k + i + 1);
            rrfScores.merge(result.chunkId(), rrfScore, Double::sum);
            resultLookup.putIfAbsent(result.chunkId(), result);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    VectorIndexPort.SearchResult source = resultLookup.get(entry.getKey());
                    return new SearchResultView(source.chunkId(), source.content(), entry.getValue().floatValue(), source.metadata());
                })
                .toList();
    }

    private List<? extends VectorIndexPort.SearchResult> mergeMultiQueryResults(
            List<List<? extends VectorIndexPort.SearchResult>> allVectorResults,
            List<List<? extends VectorIndexPort.SearchResult>> allKeywordResults,
            int topK) {
        int k = ragProperties.getRrfK();
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, VectorIndexPort.SearchResult> resultLookup = new LinkedHashMap<>();

        for (List<? extends VectorIndexPort.SearchResult> vectorResults : allVectorResults) {
            for (int i = 0; i < vectorResults.size(); i++) {
                VectorIndexPort.SearchResult result = vectorResults.get(i);
                double rrfScore = 1.0 / (k + i + 1);
                rrfScores.merge(result.chunkId(), rrfScore, Double::sum);
                resultLookup.putIfAbsent(result.chunkId(), result);
            }
        }

        for (List<? extends VectorIndexPort.SearchResult> keywordResults : allKeywordResults) {
            for (int i = 0; i < keywordResults.size(); i++) {
                VectorIndexPort.SearchResult result = keywordResults.get(i);
                double rrfScore = 1.0 / (k + i + 1);
                rrfScores.merge(result.chunkId(), rrfScore, Double::sum);
                resultLookup.putIfAbsent(result.chunkId(), result);
            }
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    VectorIndexPort.SearchResult source = resultLookup.get(entry.getKey());
                    return new SearchResultView(source.chunkId(), source.content(), entry.getValue().floatValue(), source.metadata());
                })
                .toList();
    }

    private record SearchResultView(String chunkId, String content, float score, Map<String, Object> metadata)
            implements VectorIndexPort.SearchResult {}

    private record KnowledgeBaseSettings(double similarityThreshold,
                                         int vectorTopK,
                                         int keywordTopK,
                                         String promptTemplate) {}
}
