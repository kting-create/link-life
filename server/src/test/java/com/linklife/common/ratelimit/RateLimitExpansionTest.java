package com.linklife.common.ratelimit;

import com.linklife.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RateLimitExpansionTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void wxLoginRateLimitedPerIp() throws Exception {
        // 前 5 次不限流(业务码不限),第 6 次 429
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/wx-login")
                            .header("X-Real-IP", "203.0.113.9")
                            .contentType("application/json").content("{\"code\":\"c\"}"))
                    .andReturn();
        }
        mvc.perform(post("/api/auth/wx-login")
                        .header("X-Real-IP", "203.0.113.9")
                        .contentType("application/json").content("{\"code\":\"c\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void refreshRateLimitedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/refresh")
                            .header("X-Real-IP", "203.0.113.10")
                            .contentType("application/json").content("{\"refreshToken\":\"bad\"}"))
                    .andReturn();
        }
        mvc.perform(post("/api/auth/refresh")
                        .header("X-Real-IP", "203.0.113.10")
                        .contentType("application/json").content("{\"refreshToken\":\"bad\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(4001));
    }
}
