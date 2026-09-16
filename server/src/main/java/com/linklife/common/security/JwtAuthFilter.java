package com.linklife.common.security;

import com.linklife.auth.jwt.JwtService;
import com.linklife.auth.jwt.TokenInfo;
import com.linklife.common.web.Result;
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

    private static final List<String> PUBLIC_PATHS =
            List.of("/api/health", "/api/auth/");

    private final JwtService jwtService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/health") || path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response);
            return;
        }
        try {
            TokenInfo info = jwtService.parse(header.substring(7));
            if (!"access".equals(info.type())) {
                reject(response);
                return;
            }
            UserContext.set(info.userId());
            chain.doFilter(request, response);
        } catch (JwtException e) {
            reject(response);
        } finally {
            UserContext.clear();
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(Result.error(2002, "登录状态无效")));
    }
}
