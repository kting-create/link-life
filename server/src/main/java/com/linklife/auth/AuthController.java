package com.linklife.auth;

import com.linklife.auth.dto.AuthTokens;
import com.linklife.auth.dto.LoginRequest;
import com.linklife.auth.dto.RefreshRequest;
import com.linklife.common.web.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/wx-login")
    public Result<AuthTokens> wxLogin(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.wxLogin(request.code()));
    }

    @PostMapping("/refresh")
    public Result<AuthTokens> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request.refreshToken()));
    }
}
