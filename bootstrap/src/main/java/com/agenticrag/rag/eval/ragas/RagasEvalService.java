package com.agenticrag.rag.eval.ragas;

import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.port.vector.VectorIndexPort;
import com.agenticrag.rag.eval.ragas.metrics.*;
import com.agenticrag.rag.query.RagQueryResult;
import com.agenticrag.rag.query.RagQueryService;
import com.agenticrag.user.service.UserAiProviderConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagasEvalService {

    private final RagQueryService ragQueryService;
    private final FaithfulnessMetric faithfulnessMetric;
    private final ContextRecallMetric contextRecallMetric;
    private final ContextPrecisionMetric contextPrecisionMetric;
    private final AnswerRelevancyMetric answerRelevancyMetric;
    private final AnswerCorrectnessMetric answerCorrectnessMetric;
    private final UserAiProviderConfigService userAiProviderConfigService;

    public RagasEvalService(RagQueryService ragQueryService,
                            FaithfulnessMetric faithfulnessMetric,
                            ContextRecallMetric contextRecallMetric,
                            ContextPrecisionMetric contextPrecisionMetric,
                            AnswerRelevancyMetric answerRelevancyMetric,
                            AnswerCorrectnessMetric answerCorrectnessMetric,
                            UserAiProviderConfigService userAiProviderConfigService) {
        this.ragQueryService = ragQueryService;
        this.faithfulnessMetric = faithfulnessMetric;
        this.contextRecallMetric = contextRecallMetric;
        this.contextPrecisionMetric = contextPrecisionMetric;
        this.answerRelevancyMetric = answerRelevancyMetric;
        this.answerCorrectnessMetric = answerCorrectnessMetric;
        this.userAiProviderConfigService = userAiProviderConfigService;
    }

    public RagasReport evaluate(String kbId, List<RagasSample> samples, String userId) {
        String evalRunId = UUID.randomUUID().toString().substring(0, 20);
        log.info("Starting RAGAS evaluation: runId={}, kbId={}, samples={}", evalRunId, kbId, samples.size());

        List<RagasResult> results = new ArrayList<>();

        for (RagasSample sample : samples) {
            try {
                RagasResult result = evaluateSample(kbId, sample, userId);
                results.add(result);
                log.info("Evaluated sample {}: faithfulness={}, recall={}, precision={}",
                        sample.id(),
                        result.faithfulness(),
                        result.contextRecall(),
                        result.contextPrecision());
            } catch (Exception e) {
                log.error("Failed to evaluate sample {}: {}", sample.id(), e.getMessage());
            }
        }

        return RagasReport.from(evalRunId, kbId, results);
    }

    public RagasResult evaluateSample(String kbId, RagasSample sample, String userId) {
        AiRuntimeContext context = userAiProviderConfigService.resolveRuntimeContext(userId);

        // 1. 执行 RAG 查询，为每个样本生成唯一的 conversationId 以确保无状态性
        String conversationId = "eval:rag:" + java.util.UUID.randomUUID().toString().substring(0, 8);
        RagQueryResult queryResult = ragQueryService.queryDetailed(sample.question(), kbId, userId, context, conversationId, 5);

        String answer = queryResult.answer();
        List<String> contexts = queryResult.retrievedChunks().stream()
                .map(RagQueryResult.RetrievedChunk::content)
                .collect(Collectors.toList());

        // 2. 并行计算各项指标
        CompletableFuture<Double> faithfulnessFuture = CompletableFuture.supplyAsync(() ->
                faithfulnessMetric.calculate(sample.question(), answer, contexts, context, userId));

        CompletableFuture<Double> contextRecallFuture = CompletableFuture.supplyAsync(() ->
                StringUtils.hasText(sample.groundTruth())
                        ? contextRecallMetric.calculate(sample.question(), sample.groundTruth(), contexts, context, userId)
                        : null);

        CompletableFuture<Double> contextPrecisionFuture = CompletableFuture.supplyAsync(() ->
                StringUtils.hasText(sample.groundTruth())
                        ? contextPrecisionMetric.calculate(sample.question(), sample.groundTruth(), contexts, context, userId)
                        : null);

        CompletableFuture<Double> answerRelevancyFuture = CompletableFuture.supplyAsync(() ->
                answerRelevancyMetric.calculate(sample.question(), answer, context, userId));

        CompletableFuture<Double> answerCorrectnessFuture = CompletableFuture.supplyAsync(() ->
                StringUtils.hasText(sample.groundTruth())
                        ? answerCorrectnessMetric.calculate(sample.question(), answer, sample.groundTruth(), context, userId)
                        : null);

        // 3. 等待所有指标计算完成
        CompletableFuture.allOf(faithfulnessFuture, contextRecallFuture,
                contextPrecisionFuture, answerRelevancyFuture, answerCorrectnessFuture).join();

        // 4. 构建结果
        return new RagasResult(
                sample.id(),
                sample.question(),
                sample.groundTruth(),
                answer,
                contexts,
                safeGet(faithfulnessFuture),
                safeGet(answerRelevancyFuture),
                safeGet(contextPrecisionFuture),
                safeGet(contextRecallFuture),
                safeGet(answerCorrectnessFuture)
        );
    }

    private Double safeGet(CompletableFuture<Double> future) {
        try {
            Double value = future.get();
            return (value != null && value >= 0) ? value : null;
        } catch (Exception e) {
            return null;
        }
    }
}
