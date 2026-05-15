package com.agenticrag.rag.query;

import com.agenticrag.infra.ai.model.AiRuntimeContext;
import com.agenticrag.infra.ai.port.vector.VectorIndexPort;

import java.util.List;

public interface AnswerVerificationService {

    /**
     * 评估检索结果对问题的支撑程度
     */
    EvidenceQuality evaluateEvidence(String query, List<? extends VectorIndexPort.SearchResult> results, AiRuntimeContext context);

    /**
     * 评估生成的回答是否忠实于检索到的上下文（Faithfulness）
     */
    FaithfulnessResult verifyFaithfulness(String query, String answer, List<? extends VectorIndexPort.SearchResult> results, AiRuntimeContext context);

    record EvidenceQuality(boolean sufficient, double score, String reason) {}
    
    record FaithfulnessResult(boolean faithful, double score, String reason) {}
}
