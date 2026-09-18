package com.linklife.recipe.service;

import com.linklife.ai.gateway.AiGatewayService;
import com.linklife.ai.gateway.AiResponseParser;
import com.linklife.ai.prompt.RecipePrompts;
import com.linklife.circle.CircleService;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.recipe.dto.IterationResult;
import com.linklife.recipe.dto.RecipeContent;
import com.linklife.recipe.entity.PantryItem;
import com.linklife.recipe.service.RecipeService.IterateContext;
import com.linklife.recipe.sse.RecipeStreamService;
import com.linklife.user.service.TasteProfileService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeGenerationService {

    private final AiGatewayService aiGatewayService;
    private final RecipeService recipeService;
    private final PantryService pantryService;
    private final TasteProfileService tasteProfileService;
    private final CircleService circleService;
    private final RecipeStreamService streamService;

    public SseEmitter generate(long userId, long circleId, String dishName) {
        // 同步校验：失败走全局异常处理（JSON 错误），不进流
        circleService.requireMembership(userId, circleId);
        List<PantryItem> pantry = pantryService.list(userId);
        String tasteSummary = tasteProfileService.getSummary(userId);
        String prompt = RecipePrompts.generate(dishName, 2, pantry, tasteSummary);
        return stream(userId, "recipe_generate", prompt, raw -> {
            RecipeContent content = AiResponseParser.parse(raw, RecipeContent.class);
            content.validate();
            long recipeId = recipeService.createRecipeWithV1(userId, circleId, dishName, content);
            return new long[]{recipeId, 1};
        });
    }

    public SseEmitter iterate(long userId, long recipeId, String comment) {
        IterateContext ctx = recipeService.prepareIterate(userId, recipeId);
        String prompt = RecipePrompts.iterate(ctx.dishName(), ctx.current(), ctx.feedbacks());
        return stream(userId, "recipe_iterate", prompt, raw -> {
            IterationResult result = AiResponseParser.parse(raw, IterationResult.class);
            result.validate();
            int version = recipeService.saveIterated(userId, recipeId, result, comment);
            return new long[]{recipeId, version};
        });
    }

    private interface PersistFn {
        long[] apply(String raw);
    }

    private SseEmitter stream(long userId, String scene, String prompt, PersistFn persist) {
        SseEmitter emitter = streamService.newEmitter();
        streamService.runAsync(emitter, () -> doStream(userId, scene, prompt, persist, emitter));
        return emitter;
    }

    private void doStream(long userId, String scene, String prompt, PersistFn persist,
                          SseEmitter emitter) {
        StringBuilder buffer = new StringBuilder();
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        emitter.onCompletion(() -> {
            Disposable d = subscription.get();
            if (d != null && !d.isDisposed()) {
                d.dispose();
            }
        });
        try {
            Flux<String> flux = aiGatewayService.stream(userId, scene, prompt)
                    .timeout(Duration.ofMillis(RecipeStreamService.TIMEOUT_MS - 5_000));
            subscription.set(flux.subscribe(
                    chunk -> {
                        buffer.append(chunk);
                        streamService.sendDelta(emitter, chunk);
                    },
                    err -> {
                        log.warn("recipe stream error, scene={}", scene, err);
                        streamService.sendError(emitter, ErrorCode.RECIPE_AI_FAILED);
                        streamService.complete(emitter);
                    },
                    () -> {
                        try {
                            long[] ids = persist.apply(buffer.toString());
                            streamService.sendDone(emitter, ids[0], (int) ids[1]);
                        } catch (Exception e) {
                            log.warn("recipe persist failed, scene={}", scene, e);
                            aiGatewayService.logFailure(userId, scene,
                                    "PERSIST_FAILED: " + e.getMessage());
                            ErrorCode code = e instanceof BusinessException be
                                    ? be.getErrorCode() : ErrorCode.RECIPE_PARSE_FAILED;
                            streamService.sendError(emitter, code);
                        } finally {
                            streamService.complete(emitter);
                        }
                    }));
        } catch (Exception e) {
            // 单一错误路径：发送 error 并收尾，不向上抛，避免 runAsync 兜底再发一次
            aiGatewayService.logFailure(userId, scene, "SUBSCRIBE_FAILED: " + e.getMessage());
            streamService.sendError(emitter, ErrorCode.RECIPE_AI_FAILED);
            streamService.complete(emitter);
        }
    }
}
