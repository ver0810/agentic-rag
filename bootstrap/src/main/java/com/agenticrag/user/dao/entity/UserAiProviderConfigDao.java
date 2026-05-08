package com.agenticrag.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_user_ai_provider_config")
public class UserAiProviderConfigDao {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String userId;

    private String provider;

    private String baseUrl;

    private String apiKey;

    private String chatModel;

    private String embeddingModel;

    private Integer verified;

    private LocalDateTime lastVerifiedAt;

    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
