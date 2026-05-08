package com.agenticrag.user.controller;

import com.agenticrag.user.dto.*;
import com.agenticrag.user.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public UserDTO login(@RequestBody UserLoginRequest request) {
        return userService.login(request);
    }

    @PostMapping("/password/update")
    public void updatePassword(@RequestHeader("X-User-Id") String userId, @RequestBody UserUpdatePasswordRequest request) {
        userService.updatePassword(userId, request);
    }

    @GetMapping("/info")
    public UserDTO getUserInfo(@RequestHeader("X-User-Id") String userId) {
        return userService.getUserInfo(userId);
    }

    @GetMapping("/list")
    public List<UserDTO> listUsers() {
        return userService.listUsers();
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable String id) {
        userService.removeById(id);
    }
}
