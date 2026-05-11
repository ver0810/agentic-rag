package com.agenticrag.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_document")
public class KnowledgeDocumentEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String kbId;
    private String docName;
    private Integer enabled;
    private Integer chunkCount;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private String processMode;
    private String status;
    private String sourceType;
    private String sourceLocation;
    private Integer scheduleEnabled;
    private String scheduleCron;
    private String chunkStrategy;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String chunkConfig;
    private String pipelineId;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
