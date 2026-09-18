package com.linklife.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linklife.IntegrationTestBase;
import com.linklife.auth.jwt.JwtService;
import com.linklife.common.web.Result;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Import({GlobalExceptionHandlerTest.ThrowController.class,
        GlobalExceptionHandlerTest.BuiltInValidationController.class})
class GlobalExceptionHandlerTest extends IntegrationTestBase {

    @Validated
    @RestController
    static class ThrowController {
        @GetMapping("/test/biz-error")
        public Result<Void> biz() {
            throw new BusinessException(ErrorCode.CIRCLE_NOT_FOUND);
        }
        @GetMapping("/test/raw-error")
        public Result<Void> raw() {
            throw new IllegalStateException("boom");
        }
        @GetMapping("/test/param-validation")
        public Result<Void> paramValidation(@RequestParam @Min(0) long id) {
            return Result.ok();
        }
    }

    @RestController
    static class BuiltInValidationController {
        @GetMapping("/test/param-validation-builtin")
        public Result<Void> paramValidation(@RequestParam @Min(0) long id) {
            return Result.ok();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    private String authHeader() {
        return "Bearer " + jwtService.generateAccessToken(ensureUser(), 0);
    }

    // JwtAuthFilter 现会校验 token_version 与 DB 中 user 是否存在,需保证用户真实存在
    private long ensureUser() {
        String openid = "global-exception-handler-test";
        try {
            return jdbc.queryForObject("SELECT id FROM `user` WHERE openid = ?", Long.class, openid);
        } catch (EmptyResultDataAccessException e) {
            jdbc.update("INSERT INTO `user`(openid, nickname, created_at, updated_at) "
                    + "VALUES (?, 't', NOW(), NOW())", openid);
            return jdbc.queryForObject("SELECT id FROM `user` WHERE openid = ?", Long.class, openid);
        }
    }

    @Test
    void businessErrorMapped() throws Exception {
        mockMvc.perform(get("/test/biz-error").header("Authorization", authHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("圈子不存在"));
    }

    @Test
    void unexpectedErrorMapped() throws Exception {
        mockMvc.perform(get("/test/raw-error").header("Authorization", authHeader()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void paramValidationErrorMapped() throws Exception {
        mockMvc.perform(get("/test/param-validation").param("id", "-5")
                .header("Authorization", authHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void builtInParamValidationErrorMapped() throws Exception {
        mockMvc.perform(get("/test/param-validation-builtin").param("id", "-5")
                .header("Authorization", authHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void unknownPathMappedTo404() throws Exception {
        // Unknown paths go through JwtAuthFilter (non-public path), so a valid Bearer
        // access token is provided to make the test deterministic (otherwise 401).
        mockMvc.perform(get("/api/nonexistent").header("Authorization", authHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void wrongMethodOnHealthMappedTo405() throws Exception {
        mockMvc.perform(post("/api/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }
}
