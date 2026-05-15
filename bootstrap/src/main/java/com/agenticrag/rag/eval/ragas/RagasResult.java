package com.agenticrag.rag.eval.ragas;

import java.util.List;

public record RagasResult(
        String sampleId,
        String question,
        String groundTruth,
        String answer,
        List<String> contexts,
        Double faithfulness,
        Double answerRelevancy,
        Double contextPrecision,
        Double contextRecall,
        Double answerCorrectness
) {
    public double overallScore() {
        int count = 0;
        double sum = 0;
        if (faithfulness != null) { sum += faithfulness; count++; }
        if (answerRelevancy != null) { sum += answerRelevancy; count++; }
        if (contextPrecision != null) { sum += contextPrecision; count++; }
        if (contextRecall != null) { sum += contextRecall; count++; }
        if (answerCorrectness != null) { sum += answerCorrectness; count++; }
        return count > 0 ? sum / count : 0;
    }
}
