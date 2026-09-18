package com.linklife.recipe.sse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SseEmitter 的容器超时由 Servlet 容器触发，MockMvc 下无法真实驱动 90s timeout，
 * 且未初始化的 SseEmitter.complete() 不会触发 onCompletion 回调，无法据此断言。
 * 因此用 mock 验证契约：wireTimeoutGuard 注册 onTimeout；handleTimeout 走 error 发送 + complete 收尾。
 * send/complete 内部已吞异常，真实 emitter 上即使连接已断也不会抛出。
 */
class RecipeStreamServiceTimeoutTest {

    @Test
    void handleTimeoutSendsErrorThenCompletes() throws Exception {
        RecipeStreamService service = new RecipeStreamService(Runnable::run);
        SseEmitter emitter = mock(SseEmitter.class);
        doNothing().when(emitter).complete();

        assertDoesNotThrow(() -> service.handleTimeout(emitter));
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    void wireTimeoutGuardRegistersOnTimeoutCallback() {
        RecipeStreamService service = new RecipeStreamService(Runnable::run);
        SseEmitter emitter = mock(SseEmitter.class);

        assertDoesNotThrow(() -> service.wireTimeoutGuard(emitter));
        verify(emitter).onTimeout(any());
    }
}
