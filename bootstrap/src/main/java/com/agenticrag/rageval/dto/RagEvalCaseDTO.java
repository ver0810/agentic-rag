package com.agenticrag.rageval.dto;

import java.util.List;

public record RagEvalCaseDTO(
        String id,
        String kbId,
        String query,
        Integer topK,
        List<String> expectedAnswerContains,
        List<String> expectedDocNames,
        boolean shouldRefuse
) {
}
