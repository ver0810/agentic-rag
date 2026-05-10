package com.agenticrag.infra.ai.rag.query;

import java.util.Map;

public interface RagTraceRecorder {

    String startRun(String traceName, String entryMethod, String conversationId, String userId, Map<String, Object> extraData);

    void completeRun(String traceId, Map<String, Object> extraData);

    void failRun(String traceId, String errorMessage, Map<String, Object> extraData);

    String startNode(String traceId, String nodeType, String nodeName, Map<String, Object> extraData);

    void completeNode(String traceId, String nodeId, Map<String, Object> extraData);

    void failNode(String traceId, String nodeId, String errorMessage, Map<String, Object> extraData);
}
