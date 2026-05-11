package com.agenticrag.feedback;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_rag_feedback")
public class FeedbackEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String traceId;
    private String kbId;
    private String userId;
    private String query;
    private String answer;
    private Integer rating;
    private String comment;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
