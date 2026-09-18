package com.linklife.ai.gateway;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfig {

    @Bean
    public ChatClient deepseekChatClient(
            org.springframework.ai.deepseek.DeepSeekChatModel model) {
        return ChatClient.create(model);
    }

    @Bean
    public ChatClient visionChatClient(OpenAiChatModel model) {
        return ChatClient.create(model);
    }
}
