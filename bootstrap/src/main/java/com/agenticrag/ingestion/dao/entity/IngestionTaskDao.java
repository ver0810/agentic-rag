package com.agenticrag.ingestion.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ingestion_task")
public class IngestionTaskDao {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String pipelineId;
    private String sourceType;
    private String sourceLocation;
    private String sourceFileName;
    private String status;
    private Integer chunkCount;
    private String errorMessage;
    private String logsJson;
    private String metadataJson;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime nextRunAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
