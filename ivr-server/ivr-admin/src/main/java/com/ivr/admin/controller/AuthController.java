package com.ivr.admin.controller;

import com.ivr.admin.dto.AuthResult;
import com.ivr.admin.dto.LoginRequest;
import com.ivr.admin.dto.RegisterRequest;
import com.ivr.admin.dto.UserInfoResponse;
import com.ivr.admin.service.AuthService;
import com.ivr.common.result.R;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 登录/注册 & 当前用户信息。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public R<AuthResult> login(@Valid @RequestBody LoginRequest params) {
        return R.ok(authService.login(params));
    }

    @PostMapping("/register")
    public R<AuthResult> register(@Valid @RequestBody RegisterRequest params) {
        return R.ok(authService.register(params));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        return R.ok();
    }

    @GetMapping("/userInfo")
    public R<UserInfoResponse> userInfo(@AuthenticationPrincipal Long userId) {
        return R.ok(authService.currentUser(userId));
    }
}
