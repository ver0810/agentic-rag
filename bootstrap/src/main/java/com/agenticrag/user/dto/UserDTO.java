package com.agenticrag.user.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDTO {
    private String id;
    private String username;
    private String avatar;
    private String role;
    private LocalDateTime createTime;
}
