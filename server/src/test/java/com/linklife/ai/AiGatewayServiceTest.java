package com.linklife.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.linklife.IntegrationTestBase;
import com.linklife.ai.gateway.AiCallLogger;
import com.linklife.ai.gateway.AiGatewayService;
import com.linklife.common.exception.BusinessException;
import com.linklife.recipe.dto.RecipeContent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

class AiGatewayServiceTest extends IntegrationTestBase {

    static final String REPLY_JSON = "{\"servings\":2,\"totalMinutes\":30,"
            + "\"ingredients\":[{\"name\":\"鸡蛋\",\"amount\":\"3个\"}],\"seasonings\":[],"
            + "\"steps\":[{\"no\":1,\"text\":\"打蛋\",\"durationSec\":60}],\"tips\":\"\"}";

    /** 预取 deep-stub 链端点：各测试用 doReturn/doThrow 重桩，避免共享链上 thenThrow 在重桩时提前触发。 */
    @TestConfiguration
    static class FakeChatConfig {

        static ChatClient.CallResponseSpec callSpec;
        static ChatClient.StreamResponseSpec streamSpec;
        static ChatClient visionClient;

        @Bean
        @org.springframework.context.annotation.Primary
        ChatClient deepseekChatClient() {
            ChatClient client = Mockito.mock(ChatClient.class,
                    Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
            ChatClient.ChatClientRequestSpec requestSpec = client.prompt().user("prompt");
            callSpec = requestSpec.call();
            streamSpec = requestSpec.stream();
            return client;
        }

        @Bean
        @org.springframework.context.annotation.Primary
        ChatClient visionChatClient() {
            visionClient = Mockito.mock(ChatClient.class,
                    Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
            return visionClient;
        }
    }

    @MockBean
    private AiCallLogger aiCallLogger;

    @Autowired
    private AiGatewayService aiGatewayService;

    @Test
    void callReturnsContentAndLogsWithTokens() {
        doReturn(deepChatResponse(REPLY_JSON, 10, 20)).when(FakeChatConfig.callSpec).chatResponse();

        String answer = aiGatewayService.call(42L, "recipe_generate", "prompt");

        assertEquals(REPLY_JSON, answer);
        verify(aiCallLogger).log(eq(42L), eq("recipe_generate"), anyString(), anyString(),
                eq(true), isNull(), eq(10), eq(20));
    }

    @Test
    void callLogsFailureAndRethrows() {
        doThrow(new RuntimeException("boom")).when(FakeChatConfig.callSpec).chatResponse();

        assertThrows(RuntimeException.class,
                () -> aiGatewayService.call(42L, "recipe_generate", "prompt"));

        verify(aiCallLogger).log(eq(42L), eq("recipe_generate"), anyString(), anyString(),
                eq(false), any(), isNull(), isNull());
    }

    @Test
    void callStructuredParsesAndValidates() {
        doReturn(deepChatResponse(REPLY_JSON, null, null))
                .when(FakeChatConfig.callSpec).chatResponse();

        RecipeContent content = aiGatewayService.callStructured(
                42L, "recipe_generate", "prompt", RecipeContent.class);

        assertEquals("鸡蛋", content.ingredients().get(0).name());
    }

    @Test
    void streamEmitsChunksAndLogsSuccess() {
        doReturn(Flux.just("{\"serv", "ings\":2}"))
                .when(FakeChatConfig.streamSpec).content();

        List<String> chunks = aiGatewayService.stream(42L, "recipe_generate", "prompt")
                .collectList().block();

        assertEquals(List.of("{\"serv", "ings\":2}"), chunks);
        verify(aiCallLogger).log(eq(42L), eq("recipe_generate"), anyString(), anyString(),
                eq(true), isNull(), isNull(), isNull());
    }

    @Test
    void streamLogsFailure() {
        doReturn(Flux.error(new RuntimeException("net down")))
                .when(FakeChatConfig.streamSpec).content();

        assertThrows(RuntimeException.class,
                () -> aiGatewayService.stream(42L, "recipe_generate", "prompt").blockLast());

        verify(aiCallLogger).log(eq(42L), eq("recipe_generate"), anyString(), anyString(),
                eq(false), any(), isNull(), isNull());
    }

    @Test
    void callStructuredWithImageParsesAndLogsQwenProvider() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class,
                Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        ChatClient.CallResponseSpec visionCallSpec = mock(ChatClient.CallResponseSpec.class,
                Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        doReturn(visionCallSpec).when(requestSpec).call();
        doReturn(deepChatResponse(REPLY_JSON, 100, 50)).when(visionCallSpec).chatResponse();
        doReturn(requestSpec).when(FakeChatConfig.visionClient).prompt(Mockito.any(Prompt.class));

        RecipeContent content = aiGatewayService.callStructuredWithImage(
                42L, "photo_analysis", "prompt",
                new byte[]{1, 2, 3}, "image/jpeg", RecipeContent.class);

        assertEquals("鸡蛋", content.ingredients().get(0).name());
        verify(aiCallLogger).log(eq(42L), eq("photo_analysis"), anyString(), anyString(),
                eq(true), isNull(), eq(100), eq(50));
    }

    @Test
    void callStructuredWithImageWrapsFailuresAsVisionError() {
        doThrow(new RuntimeException("boom"))
                .when(FakeChatConfig.visionClient)
                .prompt(Mockito.any(Prompt.class));

        BusinessException e = assertThrows(BusinessException.class,
                () -> aiGatewayService.callStructuredWithImage(
                        42L, "photo_analysis", "prompt",
                        new byte[]{1}, "image/jpeg", RecipeContent.class));
        assertEquals(6005, e.getErrorCode().code);
    }

    @Test
    void callStructuredWithImageParseFailureLogsParseFailedAndThrowsVisionError() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class,
                Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        ChatClient.CallResponseSpec visionCallSpec = mock(ChatClient.CallResponseSpec.class,
                Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        doReturn(visionCallSpec).when(requestSpec).call();
        doReturn(deepChatResponse("这不是 JSON", null, null)).when(visionCallSpec).chatResponse();
        doReturn(requestSpec).when(FakeChatConfig.visionClient).prompt(Mockito.any(Prompt.class));

        BusinessException e = assertThrows(BusinessException.class,
                () -> aiGatewayService.callStructuredWithImage(
                        42L, "photo_analysis", "prompt",
                        new byte[]{1, 2, 3}, "image/jpeg", RecipeContent.class));
        assertEquals(6005, e.getErrorCode().code);

        verify(aiCallLogger).log(eq(42L), eq("photo_analysis"), anyString(), anyString(),
                eq(false), Mockito.contains("PARSE_FAILED"), isNull(), isNull());
    }

    /** 深桩 ChatResponse：output text 与 usage 两链。 */
    private static ChatResponse deepChatResponse(String text, Integer promptTokens,
                                                 Integer completionTokens) {
        ChatResponse response = mock(ChatResponse.class,
                Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        Mockito.doReturn(new Generation(new AssistantMessage(text))).when(response).getResult();
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Mockito.doReturn(metadata).when(response).getMetadata();
        Usage usage = mock(Usage.class);
        Mockito.doReturn(usage).when(metadata).getUsage();
        Mockito.doReturn(promptTokens).when(usage).getPromptTokens();
        Mockito.doReturn(completionTokens).when(usage).getCompletionTokens();
        return response;
    }
}
