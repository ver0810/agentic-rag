package com.agenticrag.ragtrace.service;

import com.agenticrag.infra.ai.rag.query.RagTraceRecorder;
import com.agenticrag.ragtrace.dto.RagTraceRunDTO;

import java.util.List;

public interface RagTraceService extends RagTraceRecorder {

    List<RagTraceRunDTO> listRuns(String userId, int limit);

    RagTraceRunDTO getRun(String userId, String traceId);
}
