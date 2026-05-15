package com.agenticrag.rag.eval.ragas;

import java.util.List;

public record RagasReport(
        String evalRunId,
        String kbId,
        int totalSamples,
        Double avgFaithfulness,
        Double avgAnswerRelevancy,
        Double avgContextPrecision,
        Double avgContextRecall,
        Double avgAnswerCorrectness,
        Double overallScore,
        List<RagasResult> results
) {
    public static RagasReport from(String evalRunId, String kbId, List<RagasResult> results) {
        int total = results.size();
        Double avgFaithfulness = avg(results, RagasResult::faithfulness);
        Double avgAnswerRelevancy = avg(results, RagasResult::answerRelevancy);
        Double avgContextPrecision = avg(results, RagasResult::contextPrecision);
        Double avgContextRecall = avg(results, RagasResult::contextRecall);
        Double avgAnswerCorrectness = avg(results, RagasResult::answerCorrectness);

        double overall = 0;
        int count = 0;
        if (avgFaithfulness != null) { overall += avgFaithfulness; count++; }
        if (avgAnswerRelevancy != null) { overall += avgAnswerRelevancy; count++; }
        if (avgContextPrecision != null) { overall += avgContextPrecision; count++; }
        if (avgContextRecall != null) { overall += avgContextRecall; count++; }
        if (avgAnswerCorrectness != null) { overall += avgAnswerCorrectness; count++; }

        return new RagasReport(
                evalRunId, kbId, total,
                avgFaithfulness, avgAnswerRelevancy,
                avgContextPrecision, avgContextRecall,
                avgAnswerCorrectness,
                count > 0 ? overall / count : null,
                results
        );
    }

    private interface MetricExtractor {
        Double extract(RagasResult r);
    }

    private static Double avg(List<RagasResult> results, MetricExtractor extractor) {
        double sum = 0;
        int count = 0;
        for (RagasResult r : results) {
            Double val = extractor.extract(r);
            if (val != null) {
                sum += val;
                count++;
            }
        }
        return count > 0 ? sum / count : null;
    }
}
