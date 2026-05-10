package com.agenticrag.rageval.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_rag_eval_case_result")
public class RagEvalCaseResultDao {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String evalRunId;
    private String caseId;
    private String kbId;
    private String queryText;
    private String traceId;
    private String rewrittenQuery;
    private Integer passed;
    private Integer answerPassed;
    private Integer citationPassed;
    private Integer refusalPassed;
    private Integer expectedAnswerTermCount;
    private Integer matchedAnswerTermCount;
    private String expectedDocNames;
    private String matchedDocNames;
    private String answerText;
    private String failureReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
