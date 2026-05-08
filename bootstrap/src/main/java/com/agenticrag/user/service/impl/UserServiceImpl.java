package com.agenticrag.user.service.impl;

import com.agenticrag.user.dao.entity.UserDao;
import com.agenticrag.user.dao.mapper.UserMapper;
import com.agenticrag.user.dto.*;
import com.agenticrag.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDao> implements UserService {

    @Override
    public String register(UserRegisterRequest request) {
        Assert.hasText(request.getUsername(), "用户名不能为空");
        Assert.hasText(request.getPassword(), "密码不能为空");

        // 检查用户名是否已存在
        long count = count(new LambdaQueryWrapper<UserDao>().eq(UserDao::getUsername, request.getUsername()));
        Assert.isTrue(count == 0, "用户名已存在");

        UserDao userDao = new UserDao();
        userDao.setUsername(request.getUsername());
        // 简单实现，暂不考虑加密，实际建议使用 BCrypt
        userDao.setPassword(request.getPassword());
        userDao.setAvatar(request.getAvatar());
        userDao.setRole(StringUtils.hasText(request.getRole()) ? request.getRole() : "user");

        save(userDao);
        return userDao.getId();
    }

    @Override
    public UserDTO login(UserLoginRequest request) {
        Assert.hasText(request.getUsername(), "用户名不能为空");
        Assert.hasText(request.getPassword(), "密码不能为空");

        UserDao userDao = getOne(new LambdaQueryWrapper<UserDao>()
                .eq(UserDao::getUsername, request.getUsername())
                .eq(UserDao::getPassword, request.getPassword()));

        Assert.notNull(userDao, "用户名或密码错误");

        return convertToDTO(userDao);
    }

    @Override
    public void updatePassword(String userId, UserUpdatePasswordRequest request) {
        Assert.hasText(request.getOldPassword(), "原密码不能为空");
        Assert.hasText(request.getNewPassword(), "新密码不能为空");

        UserDao userDao = getById(userId);
        Assert.notNull(userDao, "用户不存在");
        Assert.isTrue(userDao.getPassword().equals(request.getOldPassword()), "原密码错误");

        userDao.setPassword(request.getNewPassword());
        updateById(userDao);
    }

    @Override
    public UserDTO getUserInfo(String userId) {
        UserDao userDao = getById(userId);
        Assert.notNull(userDao, "用户不存在");
        return convertToDTO(userDao);
    }

    @Override
    public List<UserDTO> listUsers() {
        return list().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private UserDTO convertToDTO(UserDao userDao) {
        UserDTO dto = new UserDTO();
        dto.setId(userDao.getId());
        dto.setUsername(userDao.getUsername());
        dto.setAvatar(userDao.getAvatar());
        dto.setRole(userDao.getRole());
        dto.setCreateTime(userDao.getCreateTime());
        return dto;
    }
}
