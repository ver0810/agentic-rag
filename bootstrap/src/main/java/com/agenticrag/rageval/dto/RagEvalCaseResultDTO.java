package com.agenticrag.rageval.dto;

import java.util.List;

public record RagEvalCaseResultDTO(
        String caseId,
        String kbId,
        String query,
        String traceId,
        String rewrittenQuery,
        boolean passed,
        boolean answerPassed,
        boolean citationPassed,
        boolean refusalPassed,
        int expectedAnswerTermCount,
        int matchedAnswerTermCount,
        List<String> expectedDocNames,
        List<String> matchedDocNames,
        String answer,
        String failureReason
) {
}
