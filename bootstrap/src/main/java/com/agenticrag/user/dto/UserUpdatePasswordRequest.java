package com.agenticrag.user.dto;

import lombok.Data;

@Data
public class UserUpdatePasswordRequest {
    private String oldPassword;
    private String newPassword;
}
