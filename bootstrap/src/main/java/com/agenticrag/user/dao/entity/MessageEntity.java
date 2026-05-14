package com.agenticrag.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_message")
public class MessageEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String conversationId;
    private String userId;
    private String role;
    private String content;
    private String metadataJson;
    private String thinkingContent;
    private Integer thinkingDuration;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
