package com.linklife.notify.channel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FeishuChannelTest {

    private FeishuProperties enabledProps() {
        FeishuProperties props = new FeishuProperties();
        props.setEnabled(true);
        props.setWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/test-hook");
        return props;
    }

    @Test
    void broadcastPostsTextMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://open.feishu.cn/open-apis/bot/v2/hook/test-hook"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.msg_type").value("text"))
                .andExpect(jsonPath("$.content.text")
                        .value("[Link-Life] 小爱 发起点单「周五晚餐」，共 3 道菜，快来认领"))
                .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));

        FeishuChannel channel = new FeishuChannel(enabledProps(), builder);
        channel.broadcast("小爱 发起点单「周五晚餐」，共 3 道菜，快来认领");
        server.verify();
    }

    @Test
    void nonZeroFeishuCodeDoesNotThrow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://open.feishu.cn/open-apis/bot/v2/hook/test-hook"))
                .andRespond(withSuccess("{\"code\":19021,\"msg\":\"invalid\"}",
                        MediaType.APPLICATION_JSON));

        FeishuChannel channel = new FeishuChannel(enabledProps(), builder);
        assertDoesNotThrow(() -> channel.broadcast("任意"));
        server.verify();
    }

    @Test
    void disabledWhenConfigMissing() {
        FeishuProperties props = new FeishuProperties();
        assertFalse(new FeishuChannel(props, RestClient.builder()).enabled());

        props.setEnabled(true);
        props.setWebhookUrl("");
        assertFalse(new FeishuChannel(props, RestClient.builder()).enabled());

        props.setWebhookUrl("https://example.com/hook");
        assertTrue(new FeishuChannel(props, RestClient.builder()).enabled());
    }
}
