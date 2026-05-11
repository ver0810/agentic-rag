package com.agenticrag.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_base")
public class KnowledgeBaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String name;
    private String embeddingModel;
    private String collectionName;
    private Double similarityThreshold;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
