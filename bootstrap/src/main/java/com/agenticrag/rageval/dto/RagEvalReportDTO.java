package com.agenticrag.rageval.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RagEvalReportDTO(
        String runId,
        String dataset,
        String kbIdOverride,
        OffsetDateTime executedAt,
        Summary summary,
        List<RagEvalCaseResultDTO> cases
) {
    public record Summary(
            int total,
            int passed,
            int failed,
            double passRate,
            double answerAccuracy,
            double citationHitRate,
            double refusalAccuracy
    ) {
    }
}
