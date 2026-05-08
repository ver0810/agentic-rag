package com.agenticrag.user.service;

import com.agenticrag.user.dao.entity.UserDao;
import com.agenticrag.user.dto.*;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface UserService extends IService<UserDao> {

    /**
     * 用户注册
     */
    String register(UserRegisterRequest request);

    /**
     * 用户登录
     */
    LoginResponse login(UserLoginRequest request);

    LoginResponse refreshToken(String refreshToken);

    void logout(String accessToken, String refreshToken);

    /**
     * 修改密码
     */
    void updatePassword(String userId, UserUpdatePasswordRequest request);

    /**
     * 获取用户信息
     */
    UserDTO getUserInfo(String userId);

    /**
     * 列表获取用户（管理员用）
     */
    List<UserDTO> listUsers();
}
