package com.agenticrag.infra.ai.rag.query;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnMissingBean(RagTraceRecorder.class)
public class NoOpRagTraceRecorder implements RagTraceRecorder {

    @Override
    public String startRun(String traceName, String entryMethod, String conversationId, String userId, Map<String, Object> extraData) {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public void completeRun(String traceId, Map<String, Object> extraData) {
    }

    @Override
    public void failRun(String traceId, String errorMessage, Map<String, Object> extraData) {
    }

    @Override
    public String startNode(String traceId, String nodeType, String nodeName, Map<String, Object> extraData) {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public void completeNode(String traceId, String nodeId, Map<String, Object> extraData) {
    }

    @Override
    public void failNode(String traceId, String nodeId, String errorMessage, Map<String, Object> extraData) {
    }
}
