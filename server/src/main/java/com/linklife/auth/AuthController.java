package com.linklife.auth;

import com.linklife.auth.dto.AuthTokens;
import com.linklife.auth.dto.BindRequest;
import com.linklife.auth.dto.BindingCodeVO;
import com.linklife.auth.dto.LoginRequest;
import com.linklife.auth.dto.RefreshRequest;
import com.linklife.common.ratelimit.RateLimiter;
import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import jakarta.servlet.http.HttpServletRequest;
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
    private final BindingCodeService bindingCodeService;
    private final RateLimiter rateLimiter;

    @PostMapping("/wx-login")
    public Result<AuthTokens> wxLogin(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.wxLogin(request.code()));
    }

    @PostMapping("/refresh")
    public Result<AuthTokens> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/binding-code")
    public Result<BindingCodeVO> bindingCode() {
        return Result.ok(bindingCodeService.create(UserContext.requireUserId()));
    }

    @PostMapping("/bind")
    public Result<AuthTokens> bind(@Valid @RequestBody BindRequest request, HttpServletRequest httpRequest) {
        // bind 端点在 JwtAuthFilter 中为免登录路径（JwtAuthFilter.shouldNotFilter），
        // 无 JWT 上下文可用，因此按客户端 IP 限流（每 IP 每分钟 5 次）。
        rateLimiter.check(RateLimiter.clientIp(httpRequest) + ":bind");
        return Result.ok(bindingCodeService.bind(request.code()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(UserContext.requireUserId());
        return Result.ok();
    }
}
