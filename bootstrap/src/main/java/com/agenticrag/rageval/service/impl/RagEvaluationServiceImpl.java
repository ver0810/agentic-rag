package com.agenticrag.rageval.service.impl;

import com.agenticrag.common.ApiException;
import com.agenticrag.infra.ai.rag.query.RagQueryResult;
import com.agenticrag.infra.ai.rag.query.RagQueryService;
import com.agenticrag.knowledge.service.KnowledgeBaseService;
import com.agenticrag.rageval.dao.entity.RagEvalCaseResultDao;
import com.agenticrag.rageval.dao.entity.RagEvalRunDao;
import com.agenticrag.rageval.dao.mapper.RagEvalCaseResultMapper;
import com.agenticrag.rageval.dao.mapper.RagEvalRunMapper;
import com.agenticrag.rageval.dto.RagEvalCaseDTO;
import com.agenticrag.rageval.dto.RagEvalCaseResultDTO;
import com.agenticrag.rageval.dto.RagEvalCompareDTO;
import com.agenticrag.rageval.dto.RagEvalDatasetDTO;
import com.agenticrag.rageval.dto.RagEvalDatasetSummaryDTO;
import com.agenticrag.rageval.dto.RagEvalReportDTO;
import com.agenticrag.rageval.dto.RagEvalRunSummaryDTO;
import com.agenticrag.rageval.service.RagEvaluationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RagEvaluationServiceImpl implements RagEvaluationService {

    private static final String DATASET_PATTERN = "classpath*:rag-eval/*.json";
    private static final Set<String> REFUSAL_HINTS = Set.of(
            "无法根据现有的知识库内容回答",
            "无法确认",
            "证据不足",
            "未提及",
            "没有相关信息"
    );

    private final ObjectMapper objectMapper;
    private final RagQueryService ragQueryService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RagEvalRunMapper ragEvalRunMapper;
    private final RagEvalCaseResultMapper ragEvalCaseResultMapper;
    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public RagEvaluationServiceImpl(ObjectMapper objectMapper,
                                    RagQueryService ragQueryService,
                                    KnowledgeBaseService knowledgeBaseService,
                                    RagEvalRunMapper ragEvalRunMapper,
                                    RagEvalCaseResultMapper ragEvalCaseResultMapper) {
        this.objectMapper = objectMapper;
        this.ragQueryService = ragQueryService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ragEvalRunMapper = ragEvalRunMapper;
        this.ragEvalCaseResultMapper = ragEvalCaseResultMapper;
    }

    @Override
    public List<RagEvalDatasetSummaryDTO> listDatasets() {
        try {
            List<RagEvalDatasetSummaryDTO> datasets = new ArrayList<>();
            for (Resource resource : resourceResolver.getResources(DATASET_PATTERN)) {
                RagEvalDatasetDTO dataset = readDataset(resource);
                datasets.add(new RagEvalDatasetSummaryDTO(
                        dataset.name(),
                        dataset.description(),
                        dataset.cases() == null ? 0 : dataset.cases().size()));
            }
            return datasets.stream()
                    .sorted((left, right) -> left.name().compareToIgnoreCase(right.name()))
                    .toList();
        } catch (IOException ex) {
            throw ApiException.badRequest("rag_eval_dataset_load_failed", "无法加载评测集: " + ex.getMessage());
        }
    }

    @Override
    public RagEvalReportDTO runDataset(String datasetName, String userId, String kbIdOverride, Integer topKOverride) {
        RagEvalDatasetDTO dataset = loadDatasetByName(datasetName);
        List<RagEvalCaseDTO> cases = dataset.cases() == null ? List.of() : dataset.cases();
        if (cases.isEmpty()) {
            throw ApiException.badRequest("rag_eval_dataset_empty", "评测集为空: " + datasetName);
        }
        List<RagEvalCaseResultDTO> results = new ArrayList<>();
        int passed = 0;
        int answerPassed = 0;
        int citationPassed = 0;
        int refusalPassed = 0;
        for (RagEvalCaseDTO evalCase : cases) {
            RagEvalCaseResultDTO result = runCase(evalCase, userId, kbIdOverride, topKOverride);
            results.add(result);
            if (result.passed()) {
                passed++;
            }
            if (result.answerPassed()) {
                answerPassed++;
            }
            if (result.citationPassed()) {
                citationPassed++;
            }
            if (result.refusalPassed()) {
                refusalPassed++;
            }
        }
        int total = results.size();
        OffsetDateTime executedAt = OffsetDateTime.now();
        RagEvalReportDTO report = new RagEvalReportDTO(
                null,
                dataset.name(),
                kbIdOverride,
                executedAt,
                new RagEvalReportDTO.Summary(
                        total,
                        passed,
                        total - passed,
                        percentage(passed, total),
                        percentage(answerPassed, total),
                        percentage(citationPassed, total),
                        percentage(refusalPassed, total)
                ),
                results);
        String effectiveKbId = StringUtils.hasText(kbIdOverride)
                ? kbIdOverride
                : cases.stream().map(RagEvalCaseDTO::kbId).filter(StringUtils::hasText).findFirst().orElse(null);
        return persistReport(userId, effectiveKbId, topKOverride, report);
    }

    @Override
    public List<RagEvalRunSummaryDTO> listRuns(String userId, String datasetName, Integer limit) {
        LambdaQueryWrapper<RagEvalRunDao> query = new LambdaQueryWrapper<RagEvalRunDao>()
                .eq(RagEvalRunDao::getUserId, userId)
                .eq(RagEvalRunDao::getDeleted, 0)
                .orderByDesc(RagEvalRunDao::getExecutedAt)
                .last("limit " + Math.max(1, Math.min(limit == null ? 20 : limit, 100)));
        if (StringUtils.hasText(datasetName)) {
            query.eq(RagEvalRunDao::getDatasetName, datasetName);
        }
        return ragEvalRunMapper.selectList(query).stream()
                .map(this::toRunSummary)
                .toList();
    }

    @Override
    public RagEvalReportDTO getRun(String userId, String runId) {
        RagEvalRunDao run = requireRun(userId, runId);
        List<RagEvalCaseResultDTO> cases = ragEvalCaseResultMapper.selectList(new LambdaQueryWrapper<RagEvalCaseResultDao>()
                        .eq(RagEvalCaseResultDao::getEvalRunId, runId)
                        .eq(RagEvalCaseResultDao::getDeleted, 0)
                        .orderByAsc(RagEvalCaseResultDao::getCaseId))
                .stream()
                .map(this::toCaseResult)
                .toList();
        return new RagEvalReportDTO(
                run.getRunId(),
                run.getDatasetName(),
                run.getKbId(),
                toOffsetDateTime(run.getExecutedAt()),
                toSummary(run),
                cases
        );
    }

    @Override
    public RagEvalCompareDTO compareRuns(String userId, String baseRunId, String targetRunId) {
        RagEvalReportDTO base = getRun(userId, baseRunId);
        RagEvalReportDTO target = getRun(userId, targetRunId);
        Map<String, RagEvalCaseResultDTO> baseCases = base.cases().stream()
                .collect(Collectors.toMap(RagEvalCaseResultDTO::caseId, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<String, RagEvalCaseResultDTO> targetCases = target.cases().stream()
                .collect(Collectors.toMap(RagEvalCaseResultDTO::caseId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<RagEvalCompareDTO.CaseDelta> diffs = new ArrayList<>();
        for (String caseId : targetCases.keySet()) {
            RagEvalCaseResultDTO targetCase = targetCases.get(caseId);
            RagEvalCaseResultDTO baseCase = baseCases.get(caseId);
            String change;
            if (baseCase == null) {
                change = "added";
            } else if (baseCase.passed() == targetCase.passed()) {
                change = "unchanged";
            } else if (targetCase.passed()) {
                change = "improved";
            } else {
                change = "regressed";
            }
            diffs.add(new RagEvalCompareDTO.CaseDelta(
                    caseId,
                    baseCase == null ? null : baseCase.passed(),
                    targetCase.passed(),
                    change,
                    baseCase == null ? null : baseCase.failureReason(),
                    targetCase.failureReason(),
                    targetCase.traceId()
            ));
        }
        for (String caseId : baseCases.keySet()) {
            if (targetCases.containsKey(caseId)) {
                continue;
            }
            RagEvalCaseResultDTO baseCase = baseCases.get(caseId);
            diffs.add(new RagEvalCompareDTO.CaseDelta(
                    caseId,
                    baseCase.passed(),
                    null,
                    "removed",
                    baseCase.failureReason(),
                    null,
                    null
            ));
        }
        return new RagEvalCompareDTO(
                toRunSummary(requireRun(userId, baseRunId)),
                toRunSummary(requireRun(userId, targetRunId)),
                new RagEvalCompareDTO.MetricDelta(
                        roundDelta(target.summary().passRate() - base.summary().passRate()),
                        roundDelta(target.summary().answerAccuracy() - base.summary().answerAccuracy()),
                        roundDelta(target.summary().citationHitRate() - base.summary().citationHitRate()),
                        roundDelta(target.summary().refusalAccuracy() - base.summary().refusalAccuracy()),
                        target.summary().passed() - base.summary().passed(),
                        target.summary().failed() - base.summary().failed()
                ),
                diffs
        );
    }

    private RagEvalCaseResultDTO runCase(RagEvalCaseDTO evalCase, String userId, String kbIdOverride, Integer topKOverride) {
        String kbId = resolveKbId(evalCase, kbIdOverride);
        knowledgeBaseService.getById(kbId, userId);
        int topK = topKOverride != null ? topKOverride : (evalCase.topK() != null ? evalCase.topK() : 5);
        RagQueryResult result = ragQueryService.queryDetailed(evalCase.query(), kbId, userId, topK);

        List<String> expectedAnswerTerms = safeList(evalCase.expectedAnswerContains());
        List<String> expectedDocNames = safeList(evalCase.expectedDocNames());
        int matchedAnswerTerms = (int) expectedAnswerTerms.stream()
                .filter(term -> containsIgnoreCase(result.answer(), term))
                .count();
        boolean answerPass = expectedAnswerTerms.isEmpty() || matchedAnswerTerms == expectedAnswerTerms.size();

        List<String> matchedDocNames = result.citations().stream()
                .map(RagQueryResult.Citation::docName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        boolean citationPass = expectedDocNames.isEmpty()
                || matchedDocNames.stream().anyMatch(doc -> expectedDocNames.stream().anyMatch(expected -> equalsIgnoreCase(doc, expected)));

        boolean refusalDetected = isRefusalAnswer(result.answer()) && result.citations().isEmpty();
        boolean refusalPass = !evalCase.shouldRefuse() || refusalDetected;
        boolean passed = evalCase.shouldRefuse() ? refusalPass : answerPass && citationPass;
        String failureReason = passed ? null : buildFailureReason(evalCase.shouldRefuse(), answerPass, citationPass, refusalPass);

        return new RagEvalCaseResultDTO(
                evalCase.id(),
                kbId,
                evalCase.query(),
                result.traceId(),
                result.rewrittenQuery(),
                passed,
                answerPass,
                citationPass,
                refusalPass,
                expectedAnswerTerms.size(),
                matchedAnswerTerms,
                expectedDocNames,
                matchedDocNames,
                result.answer(),
                failureReason
        );
    }

    private RagEvalDatasetDTO loadDatasetByName(String datasetName) {
        if (!StringUtils.hasText(datasetName)) {
            throw ApiException.badRequest("rag_eval_dataset_required", "dataset 不能为空");
        }
        try {
            for (Resource resource : resourceResolver.getResources(DATASET_PATTERN)) {
                RagEvalDatasetDTO dataset = readDataset(resource);
                if (equalsIgnoreCase(dataset.name(), datasetName)) {
                    return dataset;
                }
            }
        } catch (IOException ex) {
            throw ApiException.badRequest("rag_eval_dataset_load_failed", "无法加载评测集: " + ex.getMessage());
        }
        throw ApiException.notFound("rag_eval_dataset_not_found", "未找到评测集: " + datasetName);
    }

    private RagEvalDatasetDTO readDataset(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            RagEvalDatasetDTO dataset = objectMapper.readValue(inputStream, RagEvalDatasetDTO.class);
            if (!StringUtils.hasText(dataset.name())) {
                String filename = resource.getFilename();
                return new RagEvalDatasetDTO(
                        filename == null ? "unnamed-dataset" : filename.replace(".json", ""),
                        dataset.description(),
                        dataset.cases());
            }
            return dataset;
        } catch (IOException ex) {
            throw ApiException.badRequest("rag_eval_dataset_parse_failed", "评测集解析失败: " + resource.getFilename());
        }
    }

    private String resolveKbId(RagEvalCaseDTO evalCase, String kbIdOverride) {
        String kbId = StringUtils.hasText(kbIdOverride) ? kbIdOverride : evalCase.kbId();
        if (!StringUtils.hasText(kbId)) {
            throw ApiException.badRequest("rag_eval_kb_id_required", "评测样例 " + evalCase.id() + " 缺少 kbId");
        }
        return kbId;
    }

    private boolean isRefusalAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            return true;
        }
        return REFUSAL_HINTS.stream().anyMatch(hint -> answer.contains(hint));
    }

    private boolean containsIgnoreCase(String value, String search) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(search)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.equalsIgnoreCase(right);
    }

    private String buildFailureReason(boolean shouldRefuse, boolean answerPass, boolean citationPass, boolean refusalPass) {
        if (shouldRefuse) {
            return refusalPass ? null : "expected_refusal";
        }
        List<String> reasons = new ArrayList<>();
        if (!answerPass) {
            reasons.add("answer_mismatch");
        }
        if (!citationPass) {
            reasons.add("citation_mismatch");
        }
        return reasons.stream().collect(Collectors.joining(","));
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private RagEvalReportDTO persistReport(String userId, String kbId, Integer topKOverride, RagEvalReportDTO report) {
        String runId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        RagEvalRunDao run = new RagEvalRunDao();
        run.setRunId(runId);
        run.setDatasetName(report.dataset());
        run.setKbId(kbId);
        run.setUserId(userId);
        run.setTopK(topKOverride);
        run.setTotalCount(report.summary().total());
        run.setPassedCount(report.summary().passed());
        run.setFailedCount(report.summary().failed());
        run.setPassRate(report.summary().passRate());
        run.setAnswerAccuracy(report.summary().answerAccuracy());
        run.setCitationHitRate(report.summary().citationHitRate());
        run.setRefusalAccuracy(report.summary().refusalAccuracy());
        run.setExecutedAt(toLocalDateTime(report.executedAt()));
        run.setCreateTime(now);
        run.setUpdateTime(now);
        run.setDeleted(0);
        ragEvalRunMapper.insert(run);

        for (RagEvalCaseResultDTO item : report.cases()) {
            RagEvalCaseResultDao caseResult = new RagEvalCaseResultDao();
            caseResult.setEvalRunId(runId);
            caseResult.setCaseId(item.caseId());
            caseResult.setKbId(item.kbId());
            caseResult.setQueryText(item.query());
            caseResult.setTraceId(item.traceId());
            caseResult.setRewrittenQuery(item.rewrittenQuery());
            caseResult.setPassed(boolToInt(item.passed()));
            caseResult.setAnswerPassed(boolToInt(item.answerPassed()));
            caseResult.setCitationPassed(boolToInt(item.citationPassed()));
            caseResult.setRefusalPassed(boolToInt(item.refusalPassed()));
            caseResult.setExpectedAnswerTermCount(item.expectedAnswerTermCount());
            caseResult.setMatchedAnswerTermCount(item.matchedAnswerTermCount());
            caseResult.setExpectedDocNames(writeJson(item.expectedDocNames()));
            caseResult.setMatchedDocNames(writeJson(item.matchedDocNames()));
            caseResult.setAnswerText(item.answer());
            caseResult.setFailureReason(item.failureReason());
            caseResult.setCreateTime(now);
            caseResult.setUpdateTime(now);
            caseResult.setDeleted(0);
            ragEvalCaseResultMapper.insert(caseResult);
        }

        return new RagEvalReportDTO(
                runId,
                report.dataset(),
                report.kbIdOverride(),
                report.executedAt(),
                report.summary(),
                report.cases()
        );
    }

    private RagEvalRunDao requireRun(String userId, String runId) {
        RagEvalRunDao run = ragEvalRunMapper.selectOne(new LambdaQueryWrapper<RagEvalRunDao>()
                .eq(RagEvalRunDao::getRunId, runId)
                .eq(RagEvalRunDao::getUserId, userId)
                .eq(RagEvalRunDao::getDeleted, 0)
                .last("limit 1"));
        if (run == null) {
            throw ApiException.notFound("rag_eval_run_not_found", "未找到评测运行记录: " + runId);
        }
        return run;
    }

    private RagEvalRunSummaryDTO toRunSummary(RagEvalRunDao run) {
        return new RagEvalRunSummaryDTO(
                run.getRunId(),
                run.getDatasetName(),
                run.getKbId(),
                run.getTopK(),
                toOffsetDateTime(run.getExecutedAt()),
                toSummary(run)
        );
    }

    private RagEvalReportDTO.Summary toSummary(RagEvalRunDao run) {
        return new RagEvalReportDTO.Summary(
                defaultInt(run.getTotalCount()),
                defaultInt(run.getPassedCount()),
                defaultInt(run.getFailedCount()),
                defaultDouble(run.getPassRate()),
                defaultDouble(run.getAnswerAccuracy()),
                defaultDouble(run.getCitationHitRate()),
                defaultDouble(run.getRefusalAccuracy())
        );
    }

    private RagEvalCaseResultDTO toCaseResult(RagEvalCaseResultDao item) {
        return new RagEvalCaseResultDTO(
                item.getCaseId(),
                item.getKbId(),
                item.getQueryText(),
                item.getTraceId(),
                item.getRewrittenQuery(),
                intToBool(item.getPassed()),
                intToBool(item.getAnswerPassed()),
                intToBool(item.getCitationPassed()),
                intToBool(item.getRefusalPassed()),
                defaultInt(item.getExpectedAnswerTermCount()),
                defaultInt(item.getMatchedAnswerTermCount()),
                readStringList(item.getExpectedDocNames()),
                readStringList(item.getMatchedDocNames()),
                item.getAnswerText(),
                item.getFailureReason()
        );
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.ofHours(8));
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String writeJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (IOException ignored) {
            return null;
        }
    }

    private List<String> readStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(String.class).readValue(value);
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private int boolToInt(boolean value) {
        return value ? 1 : 0;
    }

    private boolean intToBool(Integer value) {
        return value != null && value == 1;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double defaultDouble(Double value) {
        return value == null ? 0D : value;
    }

    private double roundDelta(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private double percentage(int count, int total) {
        if (total <= 0) {
            return 0D;
        }
        return Math.round((count * 10000D) / total) / 100D;
    }
}
