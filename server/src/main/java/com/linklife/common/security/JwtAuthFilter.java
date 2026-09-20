package com.linklife.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.auth.jwt.JwtService;
import com.linklife.auth.jwt.TokenInfo;
import com.linklife.common.exception.ErrorCode;
import com.linklife.common.web.Result;
import com.linklife.user.UserService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserService userService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/api/health")) {
            return true;
        }
        if (path.startsWith("/api/share/")) {
            return true;
        }
        if (path.startsWith("/api/auth/")) {
            return !path.equals("/api/auth/binding-code") && !path.equals("/api/auth/logout");
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            rejectWith(response, ErrorCode.INVALID_TOKEN);
            return;
        }
        try {
            TokenInfo info = jwtService.parse(header.substring(7));
            if (!"access".equals(info.type())) {
                rejectWith(response, ErrorCode.INVALID_TOKEN);
                return;
            }
            var user = userService.getUserById(info.userId());
            long dbVer = user == null || user.getTokenVersion() == null
                    ? 0 : user.getTokenVersion();
            if (user == null || dbVer != info.ver()) {
                rejectWith(response, ErrorCode.TOKEN_REVOKED);
                return;
            }
            UserContext.set(info.userId());
            chain.doFilter(request, response);
        } catch (JwtException e) {
            rejectWith(response, ErrorCode.INVALID_TOKEN);
        } finally {
            UserContext.clear();
        }
    }

    private void rejectWith(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                new ObjectMapper().writeValueAsString(
                        Result.error(errorCode.code, errorCode.message)));
    }
}
