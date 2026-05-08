package com.agenticrag.user.controller;

import com.agenticrag.user.auth.AuthConstants;
import com.agenticrag.user.auth.CurrentUser;
import com.agenticrag.user.auth.RefreshTokenRequest;
import com.agenticrag.user.auth.RequireRole;
import com.agenticrag.user.dto.*;
import com.agenticrag.user.service.UserService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public String register(@RequestBody UserRegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody UserLoginRequest request) {
        return userService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody RefreshTokenRequest request) {
        return userService.refreshToken(request.getRefreshToken());
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                       @RequestBody(required = false) RefreshTokenRequest request) {
        String accessToken = extractBearerToken(authorization);
        String refreshToken = request == null ? null : request.getRefreshToken();
        userService.logout(accessToken, refreshToken);
    }

    @PostMapping("/password/update")
    public void updatePassword(@CurrentUser String userId, @RequestBody UserUpdatePasswordRequest request) {
        userService.updatePassword(userId, request);
    }

    @GetMapping("/info")
    public UserDTO getUserInfo(@CurrentUser String userId) {
        return userService.getUserInfo(userId);
    }

    @GetMapping("/list")
    @RequireRole("admin")
    public List<UserDTO> listUsers() {
        return userService.listUsers();
    }

    @DeleteMapping("/{id}")
    @RequireRole("admin")
    public void deleteUser(@PathVariable String id) {
        userService.removeById(id);
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(AuthConstants.TOKEN_PREFIX)) {
            return null;
        }
        return authorization.substring(AuthConstants.TOKEN_PREFIX.length()).trim();
    }
}
