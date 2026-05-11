package com.agenticrag.rageval.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_rag_eval_run")
public class RagEvalRunEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String runId;
    private String datasetName;
    private String kbId;
    private String userId;
    private Integer topK;
    private Integer totalCount;
    private Integer passedCount;
    private Integer failedCount;
    private Double passRate;
    private Double answerAccuracy;
    private Double citationHitRate;
    private Double refusalAccuracy;
    private LocalDateTime executedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
