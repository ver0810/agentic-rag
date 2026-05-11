package com.agenticrag.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ingestion_task_node")
public class IngestionTaskNodeEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String taskId;
    private String pipelineId;
    private String nodeId;
    private String nodeType;
    private Integer nodeOrder;
    private String status;
    private Long durationMs;
    private String message;
    private String errorMessage;
    private String outputJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
