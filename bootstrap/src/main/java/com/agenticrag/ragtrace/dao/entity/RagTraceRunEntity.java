package com.agenticrag.ragtrace.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_rag_trace_run")
public class RagTraceRunEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String traceId;
    private String traceName;
    private String entryMethod;
    private String conversationId;
    private String taskId;
    private String userId;
    private String status;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String extraData;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
