package com.linklife.auth.wechat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public WeChatClient(WeChatProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
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
        // 微信该接口返回 Content-Type: text/plain 的 JSON 体，不能直接反序列化，先取原始字符串
        String body;
        try {
            body = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            log.warn("wx code2session transport error: {}", e.getMessage());
            throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
        }
        WxSessionResponse resp = parse(body);
        if (resp == null || resp.errcode() != null && resp.errcode() != 0) {
            log.warn("wx code2session failed: {}", body);
            throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
        }
        return new WxSession(resp.openid(), resp.unionid());
    }

    private WxSessionResponse parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, WxSessionResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("wx code2session invalid response body: {}", body);
            throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
        }
    }

    record WxSessionResponse(String openid, String unionid, Integer errcode, String errmsg) {
    }
}
