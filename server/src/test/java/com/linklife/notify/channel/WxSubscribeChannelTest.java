package com.linklife.notify.channel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.linklife.auth.wechat.WeChatProperties;
import com.linklife.notify.entity.Notification;
import com.linklife.notify.wechat.WxMpAccessTokenService;
import com.linklife.user.UserService;
import com.linklife.user.entity.User;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class WxSubscribeChannelTest {

    private WxSubscribeProperties enabledProps() {
        WxSubscribeProperties props = new WxSubscribeProperties();
        props.setEnabled(true);
        props.setTemplateId("TPL-ID");
        return props;
    }

    private Notification notification() {
        Notification n = new Notification();
        n.setUserId(7L);
        n.setType("ITEM_CLAIMED");
        n.setTitle("小博 认领了「番茄炒蛋」");
        n.setContent("点单「周五晚餐」");
        n.setSheetId(42L);
        return n;
    }

    private UserService userServiceWithOpenid() {
        UserService userService = mock(UserService.class);
        User user = new User();
        user.setId(7L);
        user.setOpenid("o-abc");
        when(userService.getUserById(7L)).thenReturn(user);
        return userService;
    }

    private URI sendUri() {
        return UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com")
                .path("/cgi-bin/message/subscribe/send")
                .queryParam("access_token", "TOKEN-1")
                .build().toUri();
    }

    @Test
    void sendsSubscribeMessageWithTemplateData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(sendUri()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.touser").value("o-abc"))
                .andExpect(jsonPath("$.template_id").value("TPL-ID"))
                .andExpect(jsonPath("$.page").value("pages/sheet-detail/sheet-detail?id=42"))
                .andExpect(jsonPath("$.data.thing1.value").value("小博 认领了「番茄炒蛋」"))
                .andExpect(jsonPath("$.data.thing2.value").value("点单「周五晚餐」"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}",
                        MediaType.APPLICATION_JSON));

        WxSubscribeChannel channel = new WxSubscribeChannel(enabledProps(),
                tokenService(), userServiceWithOpenid(), wxProps(), builder);
        channel.send(notification());
        server.verify();
    }

    @Test
    void userNotSubscribedDoesNotThrow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(sendUri()))
                .andRespond(withSuccess("{\"errcode\":43101,\"errmsg\":\"user refused\"}",
                        MediaType.APPLICATION_JSON));

        WxSubscribeChannel channel = new WxSubscribeChannel(enabledProps(),
                tokenService(), userServiceWithOpenid(), wxProps(), builder);
        assertDoesNotThrow(() -> channel.send(notification()));
        server.verify();
    }

    @Test
    void longValuesTruncatedTo20Chars() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(sendUri()))
                .andExpect(jsonPath("$.data.thing1.value").value("01234567890123456789"))
                .andRespond(withSuccess("{\"errcode\":0}", MediaType.APPLICATION_JSON));

        Notification n = notification();
        n.setTitle("01234567890123456789超出的部分被截断");
        WxSubscribeChannel channel = new WxSubscribeChannel(enabledProps(),
                tokenService(), userServiceWithOpenid(), wxProps(), builder);
        channel.send(n);
        server.verify();
    }

    @Test
    void disabledWhenConfigMissing() {
        WxSubscribeProperties props = new WxSubscribeProperties();
        assertFalse(new WxSubscribeChannel(props, tokenService(),
                userServiceWithOpenid(), wxProps(), RestClient.builder()).enabled());
        props.setEnabled(true);
        props.setTemplateId("");
        assertFalse(new WxSubscribeChannel(props, tokenService(),
                userServiceWithOpenid(), wxProps(), RestClient.builder()).enabled());
        props.setTemplateId("TPL");
        assertTrue(new WxSubscribeChannel(props, tokenService(),
                userServiceWithOpenid(), wxProps(), RestClient.builder()).enabled());
    }

    private WxMpAccessTokenService tokenService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?"
                        + "grant_type=client_credential&appid=wx-app&secret=wx-secret"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"TOKEN-1\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
        WeChatProperties props = new WeChatProperties();
        props.setAppid("wx-app");
        props.setSecret("wx-secret");
        props.setApiBase("https://api.weixin.qq.com");
        WxMpAccessTokenService service = new WxMpAccessTokenService(props, builder);
        service.getToken();
        return service;
    }

    private WeChatProperties wxProps() {
        WeChatProperties props = new WeChatProperties();
        props.setApiBase("https://api.weixin.qq.com");
        return props;
    }
}
