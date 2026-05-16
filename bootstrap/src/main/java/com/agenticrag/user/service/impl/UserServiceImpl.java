package com.agenticrag.user.service.impl;

import com.agenticrag.user.dao.entity.UserEntity;
import com.agenticrag.user.dao.mapper.UserMapper;
import com.agenticrag.user.dto.*;
import com.agenticrag.user.auth.AuthenticatedUser;
import com.agenticrag.user.auth.JwtTokenService;
import com.agenticrag.user.auth.TokenBlacklistService;
import com.agenticrag.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;

    public UserServiceImpl(JwtTokenService jwtTokenService,
                           PasswordEncoder passwordEncoder,
                           TokenBlacklistService tokenBlacklistService) {
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public String register(UserRegisterRequest request) {
        Assert.hasText(request.getUsername(), "用户名不能为空");
        Assert.hasText(request.getPassword(), "密码不能为空");

        // 检查用户名是否已存在
        long count = count(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, request.getUsername()));
        Assert.isTrue(count == 0, "用户名已存在");

        UserEntity userDao = new UserEntity();
        userDao.setUsername(request.getUsername());
        userDao.setPassword(passwordEncoder.encode(request.getPassword()));
        userDao.setAvatar(request.getAvatar());
        userDao.setRole(StringUtils.hasText(request.getRole()) ? request.getRole() : "user");

        save(userDao);
        return userDao.getId();
    }

    @Override
    public LoginResponse login(UserLoginRequest request) {
        Assert.hasText(request.getUsername(), "用户名不能为空");
        Assert.hasText(request.getPassword(), "密码不能为空");

        UserEntity userDao = getOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.getUsername()));
        Assert.notNull(userDao, "用户名或密码错误");
        Assert.isTrue(matchesPassword(request.getPassword(), userDao.getPassword()), "用户名或密码错误");

        if (needsPasswordUpgrade(userDao.getPassword())) {
            userDao.setPassword(passwordEncoder.encode(request.getPassword()));
            updateById(userDao);
        }

        UserDTO user = convertToDTO(userDao);
        JwtTokenService.TokenPair tokenPair = jwtTokenService.generateTokenPair(user.getId(), user.getUsername(), user.getRole());
        LoginResponse response = new LoginResponse();
        response.setUser(user);
        response.setAccessToken(tokenPair.getAccessToken());
        response.setRefreshToken(tokenPair.getRefreshToken());
        return response;
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        Assert.hasText(refreshToken, "refreshToken不能为空");
        Assert.isTrue(!tokenBlacklistService.isBlacklisted(refreshToken), "refreshToken已失效，请重新登录");

        AuthenticatedUser user = jwtTokenService.parseRefreshToken(refreshToken);
        UserEntity userDao = getById(user.getUserId());
        Assert.notNull(userDao, "用户不存在");

        tokenBlacklistService.blacklist(refreshToken, jwtTokenService.getExpiration(refreshToken));
        JwtTokenService.TokenPair tokenPair = jwtTokenService.generateTokenPair(userDao.getId(), userDao.getUsername(), userDao.getRole());
        LoginResponse response = new LoginResponse();
        response.setUser(convertToDTO(userDao));
        response.setAccessToken(tokenPair.getAccessToken());
        response.setRefreshToken(tokenPair.getRefreshToken());
        return response;
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        blacklistIfPresent(accessToken);
        blacklistIfPresent(refreshToken);
    }

    @Override
    public void updatePassword(String userId, UserUpdatePasswordRequest request) {
        Assert.hasText(request.getOldPassword(), "原密码不能为空");
        Assert.hasText(request.getNewPassword(), "新密码不能为空");

        UserEntity userDao = getById(userId);
        Assert.notNull(userDao, "用户不存在");
        Assert.isTrue(matchesPassword(request.getOldPassword(), userDao.getPassword()), "原密码错误");

        userDao.setPassword(passwordEncoder.encode(request.getNewPassword()));
        updateById(userDao);
    }

    @Override
    public UserDTO getUserInfo(String userId) {
        UserEntity userDao = getById(userId);
        Assert.notNull(userDao, "用户不存在");
        return convertToDTO(userDao);
    }

    @Override
    public List<UserDTO> listUsers() {
        return list().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private UserDTO convertToDTO(UserEntity userDao) {
        UserDTO dto = new UserDTO();
        dto.setId(userDao.getId());
        dto.setUsername(userDao.getUsername());
        dto.setAvatar(userDao.getAvatar());
        dto.setRole(userDao.getRole());
        dto.setCreateTime(userDao.getCreateTime());
        return dto;
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(storedPassword)) {
            return false;
        }
        if (needsPasswordUpgrade(storedPassword)) {
            // Direct string comparison is vulnerable to timing attacks, but required for legacy passwords
            // We use MessageDigest.isEqual for constant-time comparison
            try {
                return java.security.MessageDigest.isEqual(
                    storedPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
                    rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
            } catch (Exception e) {
                return false;
            }
        }
        return passwordEncoder.matches(rawPassword, storedPassword);
    }

    private boolean needsPasswordUpgrade(String storedPassword) {
        return !StringUtils.hasText(storedPassword) || !storedPassword.startsWith("$2");
    }

    private void blacklistIfPresent(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            Instant expiresAt = jwtTokenService.getExpiration(token);
            tokenBlacklistService.blacklist(token, expiresAt);
        } catch (Exception ignored) {
        }
    }
}
