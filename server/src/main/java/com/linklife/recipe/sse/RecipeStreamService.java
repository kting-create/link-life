package com.linklife.recipe.sse;

import com.linklife.common.exception.ErrorCode;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class RecipeStreamService {

    public static final long TIMEOUT_MS = 90_000;

    private final Executor recipeExecutor;

    public RecipeStreamService(@Qualifier("recipeExecutor") Executor recipeExecutor) {
        this.recipeExecutor = recipeExecutor;
    }

    public SseEmitter newEmitter() {
        return new SseEmitter(TIMEOUT_MS);
    }

    public void runAsync(SseEmitter emitter, Runnable work) {
        recipeExecutor.execute(() -> {
            try {
                work.run();
            } catch (Exception e) {
                // 仅兜底同步异常；正常完成由流式编排的终端回调负责 complete
                log.error("recipe stream failed", e);
                sendError(emitter, ErrorCode.RECIPE_AI_FAILED);
                complete(emitter);
            }
        });
    }

    /** 幂等收尾：重复 complete 或完成后 send 抛出的异常一律吞掉记 warn。 */
    public void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("sse complete failed", e);
        }
    }

    public void sendDelta(SseEmitter emitter, String text) {
        send(emitter, "delta", Map.of("text", text));
    }

    public void sendDone(SseEmitter emitter, long recipeId, int version) {
        send(emitter, "done", Map.of("recipeId", recipeId, "version", version));
    }

    public void sendError(SseEmitter emitter, ErrorCode code) {
        send(emitter, "error", Map.of("code", code.code, "message", code.message));
    }

    public void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.warn("sse send failed, event={}", event, e);
        }
    }
}
