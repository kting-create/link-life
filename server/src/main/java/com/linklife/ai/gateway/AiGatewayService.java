package com.linklife.ai.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    public String call(String scene, String prompt) {
        try {
            String content = chatClient.prompt().user(prompt).call().content();
            aiCallLogger.log(null, scene, provider, model, true, null);
            return content;
        } catch (Exception e) {
            log.error("ai call failed, scene={}", scene, e);
            aiCallLogger.log(null, scene, provider, model, false, truncate(e.getMessage()));
            throw e;
        }
    }

    private String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 512 ? msg.substring(0, 512) : msg;
    }
}
