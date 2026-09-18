package com.linklife.ai.gateway;

import com.linklife.recipe.dto.IterationResult;
import com.linklife.recipe.dto.RecipeContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class AiGatewayService {

    private final ChatClient chatClient;
    private final AiCallLogger aiCallLogger;
    private final String provider;
    private final String model;

    public AiGatewayService(ChatClient.Builder chatClientBuilder,
                            AiCallLogger aiCallLogger,
                            @Value("${spring.ai.deepseek.api-key:}") String apiKey,
                            @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}") String model) {
        this.chatClient = chatClientBuilder.build();
        this.aiCallLogger = aiCallLogger;
        this.provider = "deepseek";
        this.model = model;
    }

    public String call(Long userId, String scene, String prompt) {
        try {
            ChatResponse response = chatClient.prompt().user(prompt).call().chatResponse();
            Usage usage = response == null || response.getMetadata() == null
                    ? null : response.getMetadata().getUsage();
            Integer promptTokens = usage == null ? null : usage.getPromptTokens();
            Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
            aiCallLogger.log(userId, scene, provider, model, true, null,
                    promptTokens, completionTokens);
            return response == null ? "" : response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("ai call failed, scene={}", scene, e);
            aiCallLogger.log(userId, scene, provider, model, false,
                    truncate(e.getMessage()), null, null);
            throw e;
        }
    }

    /** 结构化输出：要求 prompt 已声明纯 JSON；解析失败抛 RECIPE_PARSE_FAILED。 */
    public <T> T callStructured(Long userId, String scene, String prompt, Class<T> type) {
        String raw = call(userId, scene, prompt);
        T result = AiResponseParser.parse(raw, type);
        if (result instanceof RecipeContent content) {
            content.validate();
        }
        if (result instanceof IterationResult iteration) {
            iteration.validate();
        }
        return result;
    }

    /** 流式增量文本；传输层成败在此记录（流式 usage 取不到记 null）。 */
    public Flux<String> stream(Long userId, String scene, String prompt) {
        return chatClient.prompt().user(prompt).stream().content()
                .doOnComplete(() ->
                        aiCallLogger.log(userId, scene, provider, model, true, null, null, null))
                .doOnError(e -> {
                    log.error("ai stream failed, scene={}", scene, e);
                    aiCallLogger.log(userId, scene, provider, model, false,
                            truncate(e.getMessage()), null, null);
                });
    }

    /** 供编排层记录解析/落库失败（传输成功但业务失败的场景）。 */
    public void logFailure(Long userId, String scene, String errorMsg) {
        aiCallLogger.log(userId, scene, provider, model, false, truncate(errorMsg), null, null);
    }

    private String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 512 ? msg.substring(0, 512) : msg;
    }
}
