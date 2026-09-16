package com.linklife.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linklife.common.web.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.ThrowController.class)
class GlobalExceptionHandlerTest {

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
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessErrorMapped() throws Exception {
        mockMvc.perform(get("/test/biz-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("圈子不存在"));
    }

    @Test
    void unexpectedErrorMapped() throws Exception {
        mockMvc.perform(get("/test/raw-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }
}
