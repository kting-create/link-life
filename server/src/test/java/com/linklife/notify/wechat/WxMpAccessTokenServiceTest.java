package com.linklife.notify.wechat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.linklife.auth.wechat.WeChatProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class WxMpAccessTokenServiceTest {

    private WeChatProperties props() {
        WeChatProperties props = new WeChatProperties();
        props.setAppid("wx-app");
        props.setSecret("wx-secret");
        props.setApiBase("https://api.weixin.qq.com");
        return props;
    }

    private URI tokenUri() {
        return UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com")
                .path("/cgi-bin/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", "wx-app")
                .queryParam("secret", "wx-secret")
                .build().toUri();
    }

    @Test
    void cachesTokenUntilExpiry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 只允许一次请求：第二次 getToken() 必须命中缓存
        server.expect(requestTo(tokenUri()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"access_token\":\"TOKEN-1\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));

        WxMpAccessTokenService service = new WxMpAccessTokenService(props(), builder);
        assertEquals("TOKEN-1", service.getToken());
        assertEquals("TOKEN-1", service.getToken());
        server.verify();
    }

    @Test
    void invalidateForcesRefetch() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(tokenUri()))
                .andRespond(withSuccess(
                        "{\"access_token\":\"TOKEN-1\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(tokenUri()))
                .andRespond(withSuccess(
                        "{\"access_token\":\"TOKEN-2\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));

        WxMpAccessTokenService service = new WxMpAccessTokenService(props(), builder);
        assertEquals("TOKEN-1", service.getToken());
        service.invalidate();
        assertEquals("TOKEN-2", service.getToken());
        server.verify();
    }

    @Test
    void errcodeResponseThrows() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(tokenUri()))
                .andRespond(withSuccess(
                        "{\"errcode\":40013,\"errmsg\":\"invalid appid\"}",
                        MediaType.APPLICATION_JSON));

        WxMpAccessTokenService service = new WxMpAccessTokenService(props(), builder);
        assertThrows(IllegalStateException.class, service::getToken);
        server.verify();
    }
}
