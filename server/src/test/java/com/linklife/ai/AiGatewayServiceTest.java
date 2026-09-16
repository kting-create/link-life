package com.linklife.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linklife.IntegrationTestBase;
import com.linklife.ai.gateway.AiCallLogger;
import com.linklife.ai.gateway.AiGatewayService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;

class AiGatewayServiceTest extends IntegrationTestBase {

    @TestConfiguration
    static class FakeChatConfig {
        @Bean
        ChatClient.Builder chatClientBuilder() {
            ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class,
                    Mockito.withSettings()
                            .defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
            // deep-stubs 链式桩：build().prompt().user(...).call().content() 返回固定内容
            when(builder.build()
                            .prompt()
                            .user(anyString())
                            .call()
                            .content())
                    .thenReturn("AI 回复内容");
            return builder;
        }
    }

    @MockBean
    private AiCallLogger aiCallLogger;

    @Autowired
    private AiGatewayService aiGatewayService;

    @Test
    void callReturnsContentAndWritesLog() {
        String answer = aiGatewayService.call("recipe_generate", "生成一份番茄炒蛋菜谱");
        assertEquals("AI 回复内容", answer);
        verify(aiCallLogger).log(any(), org.mockito.ArgumentMatchers.eq("recipe_generate"),
                anyString(), anyString(), org.mockito.ArgumentMatchers.eq(true), any());
    }
}
