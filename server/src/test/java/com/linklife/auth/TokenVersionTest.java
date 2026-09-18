package com.linklife.auth;

import com.linklife.IntegrationTestBase;
import com.linklife.auth.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TokenVersionTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    private long insertUser() {
        jdbc.update("INSERT INTO `user`(openid, nickname, created_at, updated_at) "
                + "VALUES (?, 't', NOW(), NOW())", "openid-" + System.nanoTime());
        return jdbc.queryForObject(
                "SELECT id FROM `user` ORDER BY id DESC LIMIT 1", Long.class);
    }

    @Test
    void accessTokenRejectedAfterLogout() throws Exception {
        long uid = insertUser();
        String token = jwtService.generateAccessToken(uid, 0);
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        // token_version 已 bump,旧 access token 被吊销
        mvc.perform(post("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(3007));
    }

    @Test
    void refreshTokenRejectedAfterLogout() throws Exception {
        long uid = insertUser();
        String refresh = jwtService.generateRefreshToken(uid, 0);
        jdbc.update("UPDATE `user` SET token_version = 1 WHERE id = ?", uid);
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(3007));
    }

    @Test
    void logoutInvalidatesRefreshTokenToo() throws Exception {
        long uid = insertUser();
        String access = jwtService.generateAccessToken(uid, 0);
        String refresh = jwtService.generateRefreshToken(uid, 0);
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        // 登出后旧 refresh token 也因 token_version bump 被吊销
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(3007));
    }
}
