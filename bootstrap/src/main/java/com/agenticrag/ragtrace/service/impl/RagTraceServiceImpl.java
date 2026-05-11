package com.agenticrag.ragtrace.service.impl;

import com.agenticrag.common.ApiException;
import com.agenticrag.common.SessionIdGenerator;
import com.agenticrag.ragtrace.dao.entity.RagTraceNodeEntity;
import com.agenticrag.ragtrace.dao.entity.RagTraceRunEntity;
import com.agenticrag.ragtrace.dao.mapper.RagTraceNodeMapper;
import com.agenticrag.ragtrace.dao.mapper.RagTraceRunMapper;
import com.agenticrag.ragtrace.dto.RagTraceNodeDTO;
import com.agenticrag.ragtrace.dto.RagTraceRunDTO;
import com.agenticrag.ragtrace.service.RagTraceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class RagTraceServiceImpl implements RagTraceService {

    private final RagTraceRunMapper ragTraceRunMapper;
    private final RagTraceNodeMapper ragTraceNodeMapper;
    private final ObjectMapper objectMapper;

    public RagTraceServiceImpl(RagTraceRunMapper ragTraceRunMapper,
                               RagTraceNodeMapper ragTraceNodeMapper,
                               ObjectMapper objectMapper) {
        this.ragTraceRunMapper = ragTraceRunMapper;
        this.ragTraceNodeMapper = ragTraceNodeMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String startRun(String traceName, String entryMethod, String conversationId, String userId, Map<String, Object> extraData) {
        String traceId = SessionIdGenerator.generate();
        RagTraceRunEntity run = new RagTraceRunEntity();
        run.setTraceId(traceId);
        run.setTraceName(traceName);
        run.setEntryMethod(entryMethod);
        run.setConversationId(abbreviate(conversationId, 20));
        run.setUserId(userId);
        run.setStatus("RUNNING");
        run.setExtraData(serialize(extraData));
        run.setStartTime(LocalDateTime.now());
        run.setCreateTime(LocalDateTime.now());
        run.setUpdateTime(LocalDateTime.now());
        run.setDeleted(0);
        ragTraceRunMapper.insert(run);
        return traceId;
    }

    @Override
    public void completeRun(String traceId, Map<String, Object> extraData) {
        RagTraceRunEntity run = requireRun(traceId);
        run.setStatus("SUCCESS");
        run.setEndTime(LocalDateTime.now());
        run.setDurationMs(durationMs(run.getStartTime(), run.getEndTime()));
        run.setExtraData(mergeExtraData(run.getExtraData(), extraData));
        run.setUpdateTime(LocalDateTime.now());
        ragTraceRunMapper.updateById(run);
    }

    @Override
    public void failRun(String traceId, String errorMessage, Map<String, Object> extraData) {
        RagTraceRunEntity run = requireRun(traceId);
        run.setStatus("ERROR");
        run.setErrorMessage(abbreviate(errorMessage));
        run.setEndTime(LocalDateTime.now());
        run.setDurationMs(durationMs(run.getStartTime(), run.getEndTime()));
        run.setExtraData(mergeExtraData(run.getExtraData(), extraData));
        run.setUpdateTime(LocalDateTime.now());
        ragTraceRunMapper.updateById(run);
    }

    @Override
    public String startNode(String traceId, String nodeType, String nodeName, Map<String, Object> extraData) {
        RagTraceNodeEntity node = new RagTraceNodeEntity();
        String nodeId = SessionIdGenerator.generate();
        node.setTraceId(abbreviate(traceId, 20));
        node.setNodeId(nodeId);
        node.setDepth(0);
        node.setNodeType(nodeType);
        node.setNodeName(nodeName);
        node.setStatus("RUNNING");
        node.setExtraData(serialize(extraData));
        node.setStartTime(LocalDateTime.now());
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());
        node.setDeleted(0);
        ragTraceNodeMapper.insert(node);
        return nodeId;
    }

    @Override
    public void completeNode(String traceId, String nodeId, Map<String, Object> extraData) {
        RagTraceNodeEntity node = requireNode(traceId, nodeId);
        node.setStatus("SUCCESS");
        node.setEndTime(LocalDateTime.now());
        node.setDurationMs(durationMs(node.getStartTime(), node.getEndTime()));
        node.setExtraData(mergeExtraData(node.getExtraData(), extraData));
        node.setUpdateTime(LocalDateTime.now());
        ragTraceNodeMapper.updateById(node);
    }

    @Override
    public void failNode(String traceId, String nodeId, String errorMessage, Map<String, Object> extraData) {
        RagTraceNodeEntity node = requireNode(traceId, nodeId);
        node.setStatus("ERROR");
        node.setErrorMessage(abbreviate(errorMessage));
        node.setEndTime(LocalDateTime.now());
        node.setDurationMs(durationMs(node.getStartTime(), node.getEndTime()));
        node.setExtraData(mergeExtraData(node.getExtraData(), extraData));
        node.setUpdateTime(LocalDateTime.now());
        ragTraceNodeMapper.updateById(node);
    }

    @Override
    public List<RagTraceRunDTO> listRuns(String userId, int limit) {
        return ragTraceRunMapper.selectList(new LambdaQueryWrapper<RagTraceRunEntity>()
                        .eq(RagTraceRunEntity::getUserId, userId)
                        .eq(RagTraceRunEntity::getDeleted, 0)
                        .orderByDesc(RagTraceRunEntity::getCreateTime)
                        .last("limit " + Math.max(1, Math.min(limit, 100))))
                .stream()
                .map(run -> new RagTraceRunDTO(
                        run.getTraceId(),
                        run.getTraceName(),
                        run.getEntryMethod(),
                        run.getConversationId(),
                        run.getUserId(),
                        run.getStatus(),
                        run.getErrorMessage(),
                        run.getDurationMs(),
                        run.getExtraData(),
                        run.getStartTime(),
                        run.getEndTime(),
                        List.of()))
                .toList();
    }

    @Override
    public RagTraceRunDTO getRun(String userId, String traceId) {
        RagTraceRunEntity run = ragTraceRunMapper.selectOne(new LambdaQueryWrapper<RagTraceRunEntity>()
                .eq(RagTraceRunEntity::getTraceId, traceId)
                .eq(RagTraceRunEntity::getUserId, userId)
                .eq(RagTraceRunEntity::getDeleted, 0)
                .last("limit 1"));
        if (run == null) {
            throw ApiException.notFound("rag_trace_not_found", "RAG trace 不存在或无权访问");
        }
        List<RagTraceNodeDTO> nodes = ragTraceNodeMapper.selectList(new LambdaQueryWrapper<RagTraceNodeEntity>()
                        .eq(RagTraceNodeEntity::getTraceId, traceId)
                        .eq(RagTraceNodeEntity::getDeleted, 0)
                        .orderByAsc(RagTraceNodeEntity::getCreateTime))
                .stream()
                .map(node -> new RagTraceNodeDTO(
                        node.getNodeId(),
                        node.getNodeType(),
                        node.getNodeName(),
                        node.getStatus(),
                        node.getErrorMessage(),
                        node.getDurationMs(),
                        node.getExtraData(),
                        node.getStartTime(),
                        node.getEndTime()))
                .toList();
        return new RagTraceRunDTO(
                run.getTraceId(),
                run.getTraceName(),
                run.getEntryMethod(),
                run.getConversationId(),
                run.getUserId(),
                run.getStatus(),
                run.getErrorMessage(),
                run.getDurationMs(),
                run.getExtraData(),
                run.getStartTime(),
                run.getEndTime(),
                nodes);
    }

    private RagTraceRunEntity requireRun(String traceId) {
        RagTraceRunEntity run = ragTraceRunMapper.selectOne(new LambdaQueryWrapper<RagTraceRunEntity>()
                .eq(RagTraceRunEntity::getTraceId, traceId)
                .eq(RagTraceRunEntity::getDeleted, 0)
                .last("limit 1"));
        if (run == null) {
            throw ApiException.notFound("rag_trace_not_found", "RAG trace 不存在");
        }
        return run;
    }

    private RagTraceNodeEntity requireNode(String traceId, String nodeId) {
        RagTraceNodeEntity node = ragTraceNodeMapper.selectOne(new LambdaQueryWrapper<RagTraceNodeEntity>()
                .eq(RagTraceNodeEntity::getTraceId, traceId)
                .eq(RagTraceNodeEntity::getNodeId, nodeId)
                .eq(RagTraceNodeEntity::getDeleted, 0)
                .last("limit 1"));
        if (node == null) {
            throw ApiException.notFound("rag_trace_node_not_found", "RAG trace node 不存在");
        }
        return node;
    }

    private String serialize(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String mergeExtraData(String existing, Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return existing;
        }
        try {
            Map<String, Object> merged = StringUtils.hasText(existing)
                    ? objectMapper.readValue(existing, new TypeReference<>() {})
                    : new java.util.LinkedHashMap<>();
            merged.putAll(updates);
            return objectMapper.writeValueAsString(merged);
        } catch (Exception ignored) {
            return serialize(updates);
        }
    }

    private long durationMs(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0L;
        }
        return Duration.between(start, end).toMillis();
    }

    private String abbreviate(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
