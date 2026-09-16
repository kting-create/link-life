package com.linklife.auth.wechat;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class WeChatClient {

    private final WeChatProperties props;
    private final RestClient restClient;

    public WeChatClient(WeChatProperties props) {
        this.props = props;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public WxSession code2Session(String jsCode) {
        URI uri = UriComponentsBuilder.fromHttpUrl(props.getApiBase())
                .path("/sns/jscode2session")
                .queryParam("appid", props.getAppid())
                .queryParam("secret", props.getSecret())
                .queryParam("js_code", jsCode)
                .queryParam("grant_type", "authorization_code")
                .build().toUri();
        WxSessionResponse resp;
        try {
            resp = restClient.get().uri(uri)
                    .retrieve().body(WxSessionResponse.class);
        } catch (RestClientException e) {
            log.warn("wx code2session transport error: {}", e.getMessage());
            throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
        }
        if (resp == null || resp.errcode() != null && resp.errcode() != 0) {
            log.warn("wx code2session failed: {}", resp);
            throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
        }
        return new WxSession(resp.openid(), resp.unionid());
    }

    record WxSessionResponse(String openid, String unionid, Integer errcode, String errmsg) {
    }
}
