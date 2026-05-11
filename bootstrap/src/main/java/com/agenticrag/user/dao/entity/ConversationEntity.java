package com.agenticrag.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_conversation")
public class ConversationEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String conversationId;
    private String userId;
    private String title;
    private LocalDateTime lastTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
