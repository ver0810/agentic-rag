package com.agenticrag.rageval.dto;

import java.util.List;

public record RagEvalCompareDTO(
        RagEvalRunSummaryDTO baseRun,
        RagEvalRunSummaryDTO targetRun,
        MetricDelta delta,
        List<CaseDelta> caseDiffs
) {
    public record MetricDelta(
            double passRateDelta,
            double answerAccuracyDelta,
            double citationHitRateDelta,
            double refusalAccuracyDelta,
            int passedDelta,
            int failedDelta
    ) {
    }

    public record CaseDelta(
            String caseId,
            Boolean basePassed,
            Boolean targetPassed,
            String change,
            String baseFailureReason,
            String targetFailureReason,
            String targetTraceId
    ) {
    }
}
