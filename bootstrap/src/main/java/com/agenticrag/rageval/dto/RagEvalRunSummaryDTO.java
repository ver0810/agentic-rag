package com.agenticrag.rageval.dto;

import java.time.OffsetDateTime;

public record RagEvalRunSummaryDTO(
        String runId,
        String dataset,
        String kbId,
        Integer topK,
        OffsetDateTime executedAt,
        RagEvalReportDTO.Summary summary
) {
}
