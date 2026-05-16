package com.agenticrag.rag.query;

import com.agenticrag.chat.dto.ChatEvent;
import com.agenticrag.chat.dto.ChatResult;
import com.agenticrag.infra.ai.config.RagProperties;
import com.agenticrag.infra.ai.model.AiChatScene;
import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.observability.TokenCostEstimator;
import com.agenticrag.infra.ai.port.embedding.KnowledgeEmbeddingPort;
import com.agenticrag.infra.ai.port.vector.VectorIndexPort;
import com.agenticrag.infra.ai.service.AiChatService;
import com.agenticrag.knowledge.dao.entity.KnowledgeBaseEntity;
import com.agenticrag.knowledge.dao.entity.KnowledgeChunkEntity;
import com.agenticrag.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.agenticrag.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
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
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final AnswerVerificationService answerVerificationService;
    private final RagCacheService ragCacheService;
    private final Executor applicationTaskExecutor;

    private final Cache<String, KnowledgeBaseSettings> kbSettingsCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();

    public DefaultRagQueryService(KnowledgeEmbeddingPort embeddingPort,
                                  VectorIndexPort vectorIndexPort,
                                  @Lazy AiChatService chatService,
                                  RagProperties ragProperties,
                                  RagQueryRewriteService ragQueryRewriteService,
                                  RagRerankService ragRerankService,
                                  @Lazy RagTraceRecorder ragTraceRecorder,
                                  TokenCostEstimator tokenCostEstimator,
                                  KnowledgeBaseMapper knowledgeBaseMapper,
                                  KnowledgeChunkMapper knowledgeChunkMapper,
                                  AnswerVerificationService answerVerificationService,
                                  RagCacheService ragCacheService,
                                  @org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        this.embeddingPort = embeddingPort;
        this.vectorIndexPort = vectorIndexPort;
        this.chatService = chatService;
        this.ragProperties = ragProperties;
        this.ragQueryRewriteService = ragQueryRewriteService;
        this.ragRerankService = ragRerankService;
        this.ragTraceRecorder = ragTraceRecorder;
        this.tokenCostEstimator = tokenCostEstimator;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.answerVerificationService = answerVerificationService;
        this.ragCacheService = ragCacheService;
        this.applicationTaskExecutor = applicationTaskExecutor;
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
        String defaultConversationId = "rag:" + kbId + ":" + (userId == null ? "anonymous" : userId);
        return queryDetailed(query, kbId, userId, context, defaultConversationId, topK);
    }

    @Override
    public RagQueryResult queryDetailed(String query, String kbId, String userId, AiRuntimeContext context, String conversationId, int topK) {
        String traceId = ragTraceRecorder.startRun("rag_query", "RagQueryService.queryDetailed", conversationId, userId, Map.of("kbId", kbId, "query", query));
        try {
            RetrievalResult retrievalResult = retrieve(query, kbId, userId, context, topK, traceId);

            if (ragProperties.isSelfRagEnabled()) {
                String verifyNodeId = ragTraceRecorder.startNode(traceId, "verify_evidence", "evidence_quality", Map.of());
                AnswerVerificationService.EvidenceQuality quality = answerVerificationService.evaluateEvidence(query, retrievalResult.results(), context);
                ragTraceRecorder.completeNode(traceId, verifyNodeId, Map.of("sufficient", quality.sufficient(), "score", quality.score(), "reason", quality.reason()));
            }

            String prompt = String.format(retrievalResult.promptTemplate(), retrievalResult.retrievedContext(), query);
            int estimatedPromptTokens = tokenCostEstimator.estimateTokens(prompt);

            String generateNodeId = ragTraceRecorder.startNode(traceId, "generate", "answer_generation", Map.of("contextChunkCount", retrievalResult.results().size()));
            String answer = chatService.call(AiChatScene.RAG_QA, prompt, context, conversationId, userId);
            
            int estimatedAnswerTokens = tokenCostEstimator.estimateTokens(answer);
            ragTraceRecorder.completeNode(traceId, generateNodeId, Map.of("answerLength", answer.length()));

            if (ragProperties.isSelfRagEnabled()) {
                final String finalAnswer = answer;
                final RetrievalResult finalRetrievalResult = retrievalResult;
                CompletableFuture.runAsync(() -> {
                    try {
                        String faithNodeId = ragTraceRecorder.startNode(traceId, "verify_faithfulness", "answer_faithfulness", Map.of());
                        AnswerVerificationService.FaithfulnessResult faith = answerVerificationService.verifyFaithfulness(query, finalAnswer, finalRetrievalResult.results(), context);
                        ragTraceRecorder.completeNode(traceId, faithNodeId, Map.of("faithful", faith.faithful(), "score", faith.score(), "reason", faith.reason()));
                    } catch (Exception e) {
                        log.error("Async faithfulness verification failed for trace {}: {}", traceId, e.getMessage());
                    } finally {
                        completeTrace(traceId, finalRetrievalResult, finalAnswer, estimatedPromptTokens, estimatedAnswerTokens);
                    }
                }, applicationTaskExecutor);
            } else {
                completeTrace(traceId, retrievalResult, answer, estimatedPromptTokens, estimatedAnswerTokens);
            }
            
            return new RagQueryResult(
                    answer,
                    traceId,
                    retrievalResult.queries().get(0),
                    retrievalResult.results().stream().map(this::toCitation).toList(),
                    retrievalResult.results().stream().map(this::toRetrievedChunk).toList());
        } catch (Exception ex) {
            ragTraceRecorder.failRun(traceId, ex.getMessage(), Map.of());
            throw ex;
        }
    }

    @Override
    public Flux<ChatEvent> streamQueryDetailed(String query, String kbId, String userId, AiRuntimeContext context, String conversationId, int topK) {
        String traceId = ragTraceRecorder.startRun("rag_query_stream", "RagQueryService.streamQueryDetailed", conversationId, userId, Map.of("kbId", kbId, "query", query));
        try {
            RetrievalResult retrievalResult = retrieve(query, kbId, userId, context, topK, traceId);

            if (ragProperties.isSelfRagEnabled()) {
                String verifyNodeId = ragTraceRecorder.startNode(traceId, "verify_evidence", "evidence_quality", Map.of());
                AnswerVerificationService.EvidenceQuality quality = answerVerificationService.evaluateEvidence(query, retrievalResult.results(), context);
                ragTraceRecorder.completeNode(traceId, verifyNodeId, Map.of("sufficient", quality.sufficient(), "score", quality.score(), "reason", quality.reason()));
            }
            
            ChatResult metadata = new ChatResult(
                    null,
                    "rag",
                    AiChatScene.RAG_QA.code(),
                    kbId,
                    traceId,
                    retrievalResult.queries().get(0),
                    retrievalResult.results().stream().map(this::toCitation).toList(),
                    retrievalResult.results().stream().map(this::toRetrievedChunk).toList()
            );

            String prompt = String.format(retrievalResult.promptTemplate(), retrievalResult.retrievedContext(), query);
            int estimatedPromptTokens = tokenCostEstimator.estimateTokens(prompt);
            String generateNodeId = ragTraceRecorder.startNode(traceId, "generate", "answer_generation", Map.of("contextChunkCount", retrievalResult.results().size()));
            StringBuilder fullAnswer = new StringBuilder();

            Flux<ChatEvent> answerFlux = chatService.stream(AiChatScene.RAG_QA, prompt, context, conversationId, userId)
                    .map(ChatEvent::chunk)
                    .doOnNext(event -> fullAnswer.append((String) event.data()))
                    .doOnComplete(() -> {
                        ragTraceRecorder.completeNode(traceId, generateNodeId, Map.of("answerLength", fullAnswer.length()));
                        if (!ragProperties.isSelfRagEnabled()) {
                            int estimatedAnswerTokens = tokenCostEstimator.estimateTokens(fullAnswer.toString());
                            completeTrace(traceId, retrievalResult, fullAnswer.toString(), estimatedPromptTokens, estimatedAnswerTokens);
                        }
                    });

            return Flux.concat(
                    Flux.just(ChatEvent.metadata(metadata)),
                    answerFlux,
                    Mono.defer(() -> {
                        if (ragProperties.isSelfRagEnabled()) {
                            return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
                                try {
                                    String faithNodeId = ragTraceRecorder.startNode(traceId, "verify_faithfulness", "answer_faithfulness", Map.of());
                                    AnswerVerificationService.FaithfulnessResult faith = answerVerificationService.verifyFaithfulness(query, fullAnswer.toString(), retrievalResult.results(), context);
                                    ragTraceRecorder.completeNode(traceId, faithNodeId, Map.of("faithful", faith.faithful(), "score", faith.score(), "reason", faith.reason()));
                                    return ChatEvent.verification(faith);
                                } catch (Exception e) {
                                    log.error("Async faithfulness verification failed for trace {}: {}", traceId, e.getMessage());
                                    return null;
                                } finally {
                                    int estimatedAnswerTokens = tokenCostEstimator.estimateTokens(fullAnswer.toString());
                                    completeTrace(traceId, retrievalResult, fullAnswer.toString(), estimatedPromptTokens, estimatedAnswerTokens);
                                }
                            }, applicationTaskExecutor)).filter(java.util.Objects::nonNull);
                        }
                        return Mono.empty();
                    }),
                    Flux.just(ChatEvent.done())
            ).onErrorResume(ex -> {
                ragTraceRecorder.failRun(traceId, ex.getMessage(), Map.of());
                return Flux.just(ChatEvent.error(ex.getMessage()));
            });
        } catch (Exception ex) {
            ragTraceRecorder.failRun(traceId, ex.getMessage(), Map.of());
            return Flux.just(ChatEvent.error(ex.getMessage()));
        }
    }

    private List<? extends VectorIndexPort.SearchResult> expandContext(List<? extends VectorIndexPort.SearchResult> results, String traceId) {
        if (results == null || results.isEmpty()) {
            return results;
        }

        String nodeId = ragTraceRecorder.startNode(traceId, "context_expansion", "expand_" + ragProperties.getContextExpansionMode(), Map.of("mode", ragProperties.getContextExpansionMode(), "inputSize", results.size()));
        try {
            List<? extends VectorIndexPort.SearchResult> expanded;
            if ("window".equalsIgnoreCase(ragProperties.getContextExpansionMode())) {
                expanded = expandByWindow(results);
            } else if ("parent".equalsIgnoreCase(ragProperties.getContextExpansionMode())) {
                expanded = expandByParent(results);
            } else {
                expanded = results;
            }
            ragTraceRecorder.completeNode(traceId, nodeId, Map.of("outputSize", expanded.size()));
            return expanded;
        } catch (Exception ex) {
            log.error("Failed to expand context: {}", ex.getMessage());
            ragTraceRecorder.failNode(traceId, nodeId, ex.getMessage(), Map.of());
            return results;
        }
    }

    private List<? extends VectorIndexPort.SearchResult> expandByWindow(List<? extends VectorIndexPort.SearchResult> results) {
        Map<String, Set<Integer>> docToIndices = new HashMap<>();
        Map<String, String> docIdToName = new HashMap<>();

        for (VectorIndexPort.SearchResult r : results) {
            String docId = (String) r.metadata().get("docId");
            if (docId != null) {
                docIdToName.putIfAbsent(docId, (String) r.metadata().get("docName"));
                Object idxObj = r.metadata().get("chunkIndex");
                if (idxObj instanceof Number num) {
                    int idx = num.intValue();
                    int window = ragProperties.getContextWindowSize();
                    Set<Integer> indices = docToIndices.computeIfAbsent(docId, k -> new HashSet<>());
                    for (int i = idx - window; i <= idx + window; i++) {
                        if (i >= 0) {
                            indices.add(i);
                        }
                    }
                }
            }
        }

        if (docToIndices.isEmpty()) {
            return results;
        }

        List<VectorIndexPort.SearchResult> finalResults = new ArrayList<>();
        
        // Batch query for all required indices across all documents
        if (!docToIndices.isEmpty()) {
            Set<String> allDocIds = docToIndices.keySet();
            Set<Integer> allIndices = docToIndices.values().stream()
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());

            List<KnowledgeChunkEntity> allChunks = knowledgeChunkMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeChunkEntity>()
                            .in(KnowledgeChunkEntity::getDocId, allDocIds)
                            .in(KnowledgeChunkEntity::getChunkIndex, allIndices)
                            .orderByAsc(KnowledgeChunkEntity::getChunkIndex)
            );
            
            for (KnowledgeChunkEntity chunk : allChunks) {
                // Filter out chunks that don't match the specific document's required indices
                Set<Integer> requiredIndices = docToIndices.get(chunk.getDocId());
                if (requiredIndices != null && requiredIndices.contains(chunk.getChunkIndex())) {
                    String docName = docIdToName.get(chunk.getDocId());
                    finalResults.add(new SearchResultView(
                            chunk.getId(),
                            chunk.getContent(),
                            1.0f,
                            Map.of("docId", chunk.getDocId(), "chunkIndex", chunk.getChunkIndex(), "docName", docName != null ? docName : "Unknown")
                    ));
                }
            }
        }

        return finalResults;
    }

    private List<? extends VectorIndexPort.SearchResult> expandByParent(List<? extends VectorIndexPort.SearchResult> results) {
        Set<String> parentIds = new HashSet<>();
        for (VectorIndexPort.SearchResult r : results) {
            String parentId = (String) r.metadata().get("parentId");
            if (parentId != null) {
                parentIds.add(parentId);
            }
        }

        Map<String, KnowledgeChunkEntity> parentMap = new HashMap<>();
        if (!parentIds.isEmpty()) {
            List<KnowledgeChunkEntity> parents = knowledgeChunkMapper.selectBatchIds(parentIds);
            if (parents != null) {
                for (KnowledgeChunkEntity parent : parents) {
                    parentMap.put(parent.getId(), parent);
                }
            }
        }

        List<VectorIndexPort.SearchResult> finalResults = new ArrayList<>();
        for (VectorIndexPort.SearchResult r : results) {
            String parentId = (String) r.metadata().get("parentId");
            if (parentId != null) {
                KnowledgeChunkEntity parent = parentMap.get(parentId);
                if (parent != null) {
                    finalResults.add(new SearchResultView(
                            parent.getId(),
                            parent.getContent(),
                            r.score(),
                            parentMetadata(parent, r.metadata())
                    ));
                    continue;
                }
            }
            finalResults.add(r);
        }
        return finalResults;
    }

    private Map<String, Object> parentMetadata(KnowledgeChunkEntity parent, Map<String, Object> childMeta) {
        Map<String, Object> meta = new HashMap<>(childMeta);
        meta.put("isParent", true);
        meta.put("parentIndex", parent.getChunkIndex());
        return meta;
    }

    private RetrievalResult retrieve(String query, String kbId, String userId, AiRuntimeContext context, int topK, String traceId) {
        int effectiveTopK = topK > 0 ? topK : ragProperties.getDefaultTopK();
        KnowledgeBaseSettings kbSettings = resolveKnowledgeBaseSettings(kbId);
        double effectiveThreshold = kbSettings.similarityThreshold();

        List<String> queries = rewriteQuery(query, userId, context, traceId);
        String retrieveNodeId = ragTraceRecorder.startNode(traceId, "retrieve", "hybrid_retrieve", Map.of("queries", queries));
        
        try {
            Map<String, Object> filter = Map.of("kbId", kbId);
            List<List<? extends VectorIndexPort.SearchResult>> allVectorResults = new ArrayList<>();
            List<List<? extends VectorIndexPort.SearchResult>> allKeywordResults = new ArrayList<>();
            int totalEstimatedEmbeddingTokens = 0;
            double totalEstimatedEmbeddingCost = 0;

            if (ragProperties.isParallelRetrievalEnabled() && queries.size() > 1) {
                List<CompletableFuture<QueryResultPair>> futures = queries.stream()
                        .map(q -> CompletableFuture.supplyAsync(() -> performSingleRetrieve(q, effectiveTopK, effectiveThreshold, filter), applicationTaskExecutor))
                        .toList();

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                for (CompletableFuture<QueryResultPair> future : futures) {
                    QueryResultPair pair = future.join();
                    allVectorResults.add(pair.vectorResults());
                    if (pair.keywordResults() != null) {
                        allKeywordResults.add(pair.keywordResults());
                    }
                    totalEstimatedEmbeddingTokens += pair.tokens();
                    totalEstimatedEmbeddingCost += pair.cost();
                }
            } else {
                for (String q : queries) {
                    QueryResultPair pair = performSingleRetrieve(q, effectiveTopK, effectiveThreshold, filter);
                    allVectorResults.add(pair.vectorResults());
                    if (pair.keywordResults() != null) {
                        allKeywordResults.add(pair.keywordResults());
                    }
                    totalEstimatedEmbeddingTokens += pair.tokens();
                    totalEstimatedEmbeddingCost += pair.cost();
                }
            }

            List<? extends VectorIndexPort.SearchResult> results = queries.size() == 1 ? allVectorResults.get(0) : mergeMultiQueryResults(allVectorResults, allKeywordResults, effectiveTopK);
            if (ragProperties.isRerankEnabled()) {
                results = ragRerankService.rerank(query, results, effectiveTopK);
                if (ragProperties.getRerankThreshold() > 0) {
                    double threshold = ragProperties.getRerankThreshold();
                    results = results.stream().filter(r -> r.score() >= threshold).toList();
                }
            }

            if (ragProperties.isContextExpansionEnabled()) {
                results = expandContext(results, traceId);
            }

            ragTraceRecorder.completeNode(traceId, retrieveNodeId, Map.of("resultCount", results.size()));
            
            String retrievedContext = results.stream()
                    .limit(ragProperties.getMaxContextChunks())
                    .map(this::toEvidenceBlock)
                    .collect(Collectors.joining("\n\n"));

            return new RetrievalResult(queries, results, retrievedContext, kbSettings.promptTemplate(), totalEstimatedEmbeddingTokens, totalEstimatedEmbeddingCost);
        } catch (Exception ex) {
            ragTraceRecorder.failNode(traceId, retrieveNodeId, ex.getMessage(), Map.of());
            throw ex;
        }
    }

    private QueryResultPair performSingleRetrieve(String q, int effectiveTopK, double effectiveThreshold, Map<String, Object> filter) {
        int estimatedTokens = tokenCostEstimator.estimateTokens(q);
        double estimatedCost = tokenCostEstimator.estimateEmbeddingCost(estimatedTokens);

        float[] queryEmbedding = ragCacheService.getEmbedding(q);
        if (queryEmbedding == null) {
            queryEmbedding = embeddingPort.embed(q);
            ragCacheService.putEmbedding(q, queryEmbedding);
        }

        List<? extends VectorIndexPort.SearchResult> vectorResults = vectorIndexPort.search(queryEmbedding, effectiveTopK, filter)
                .stream().filter(r -> r.score() >= effectiveThreshold).toList();

        List<? extends VectorIndexPort.SearchResult> keywordResults = null;
        if (ragProperties.isHybridEnabled()) {
            keywordResults = vectorIndexPort.keywordSearch(q, effectiveTopK, filter)
                    .stream().filter(r -> r.score() >= effectiveThreshold).toList();
        }

        return new QueryResultPair(vectorResults, keywordResults, estimatedTokens, estimatedCost);
    }

    private List<String> rewriteQuery(String query, String userId, AiRuntimeContext context, String traceId) {
        if (ragProperties.isRewriteEnabled()) {
            String rewriteNodeId = ragTraceRecorder.startNode(traceId, "rewrite", "query_rewrite", Map.of("query", query));
            try {
                if (ragProperties.isMultiQueryEnabled() && ragProperties.getMultiQueryCount() > 1) {
                    List<String> rewrittenList = ragQueryRewriteService.rewriteMultiple(query, ragProperties.getMultiQueryCount(), userId, context);
                    ragTraceRecorder.completeNode(traceId, rewriteNodeId, Map.of("rewrittenQueries", rewrittenList));
                    return rewrittenList;
                } else {
                    String rewritten = ragQueryRewriteService.rewrite(query, userId, context);
                    ragTraceRecorder.completeNode(traceId, rewriteNodeId, Map.of("rewrittenQuery", rewritten));
                    return List.of(rewritten);
                }
            } catch (Exception ex) {
                ragTraceRecorder.failNode(traceId, rewriteNodeId, ex.getMessage(), Map.of());
            }
        }
        return List.of(query);
    }

    private void completeTrace(String traceId, RetrievalResult retrievalResult, String answer, int estimatedPromptTokens, int estimatedAnswerTokens) {
        double estimatedChatInputCost = tokenCostEstimator.estimateChatInputCost(estimatedPromptTokens);
        double estimatedChatOutputCost = tokenCostEstimator.estimateChatOutputCost(estimatedAnswerTokens);
        double totalCost = tokenCostEstimator.round6(retrievalResult.totalEstimatedEmbeddingCost() + estimatedChatInputCost + estimatedChatOutputCost);

        Map<String, Object> runExtraData = new LinkedHashMap<>();
        runExtraData.put("queries", retrievalResult.queries());
        runExtraData.put("answerState", determineAnswerState(answer));
        runExtraData.put("estimatedTotalTokens", retrievalResult.totalEstimatedEmbeddingTokens() + estimatedPromptTokens + estimatedAnswerTokens);
        runExtraData.put("estimatedTotalCost", totalCost);
        ragTraceRecorder.completeRun(traceId, runExtraData);
    }

    private record RetrievalResult(
            List<String> queries,
            List<? extends VectorIndexPort.SearchResult> results,
            String retrievedContext,
            String promptTemplate,
            int totalEstimatedEmbeddingTokens,
            double totalEstimatedEmbeddingCost
    ) {}

    private KnowledgeBaseSettings resolveKnowledgeBaseSettings(String kbId) {
        if (kbId == null) {
            return new KnowledgeBaseSettings(
                    ragProperties.getSimilarityThreshold(),
                    ragProperties.getVectorTopK(),
                    ragProperties.getKeywordTopK(),
                    defaultPromptTemplate());
        }
        return kbSettingsCache.get(kbId, id -> {
            KnowledgeBaseEntity kb = knowledgeBaseMapper.selectOne(
                    new LambdaQueryWrapper<KnowledgeBaseEntity>()
                            .eq(KnowledgeBaseEntity::getId, id)
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
        });
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
                metadataValue(result, "headingPath"),
                metadataValue(result, "segmentType"),
                metadataIntegerValue(result, "headingLevel"),
                result.score(),
                abbreviate(result.content(), 200));
    }

    private RagQueryResult.RetrievedChunk toRetrievedChunk(VectorIndexPort.SearchResult result) {
        return new RagQueryResult.RetrievedChunk(
                result.chunkId(),
                metadataValue(result, "docId"),
                metadataValue(result, "docName"),
                metadataIntegerValue(result, "chunkIndex"),
                metadataValue(result, "headingPath"),
                metadataValue(result, "segmentType"),
                metadataIntegerValue(result, "headingLevel"),
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
        String headingPath = metadataValue(result, "headingPath");
        String segmentType = metadataValue(result, "segmentType");
        String label = chunkIndex == null ? result.chunkId() : String.valueOf(chunkIndex + 1);
        return "[" + label + "] "
                + (docName == null ? "Unknown Document" : docName)
                + evidenceSuffix(headingPath, segmentType)
                + "\n"
                + result.content();
    }

    private String evidenceSuffix(String headingPath, String segmentType) {
        StringBuilder suffix = new StringBuilder();
        if (StringUtils.hasText(segmentType)) {
            suffix.append(" <").append(segmentType).append(">");
        }
        if (StringUtils.hasText(headingPath)) {
            suffix.append(" ").append(headingPath);
        }
        return suffix.toString();
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

    private record QueryResultPair(
            List<? extends VectorIndexPort.SearchResult> vectorResults,
            List<? extends VectorIndexPort.SearchResult> keywordResults,
            int tokens,
            double cost
    ) {}

    private record SearchResultView(String chunkId, String content, float score, Map<String, Object> metadata)
            implements VectorIndexPort.SearchResult {}

    private record KnowledgeBaseSettings(double similarityThreshold,
                                         int vectorTopK,
                                         int keywordTopK,
                                         String promptTemplate) {}
}
