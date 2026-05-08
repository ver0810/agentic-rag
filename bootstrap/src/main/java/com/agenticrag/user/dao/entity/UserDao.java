package com.agenticrag.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class UserDao {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    private String username;
    
    private String password;

    private String avatar;

    /**
     * 角色： admin / user
     */
    private String role;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

}
