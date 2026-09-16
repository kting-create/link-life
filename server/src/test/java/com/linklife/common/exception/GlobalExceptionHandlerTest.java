package com.linklife.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linklife.IntegrationTestBase;
import com.linklife.auth.jwt.JwtService;
import com.linklife.common.web.Result;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
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

    private String authHeader() {
        return "Bearer " + jwtService.generateAccessToken(1L);
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
}
