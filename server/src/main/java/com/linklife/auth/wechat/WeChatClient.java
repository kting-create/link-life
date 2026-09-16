package com.linklife.auth.wechat;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class WeChatClient {

    private final WeChatProperties props;
    private final RestClient restClient;

    public WeChatClient(WeChatProperties props) {
        this.props = props;
        this.restClient = RestClient.create();
    }

    public WxSession code2Session(String jsCode) {
        URI uri = UriComponentsBuilder.fromHttpUrl(props.getApiBase())
                .path("/sns/jscode2session")
                .queryParam("appid", props.getAppid())
                .queryParam("secret", props.getSecret())
                .queryParam("js_code", jsCode)
                .queryParam("grant_type", "authorization_code")
                .build().toUri();
        WxSessionResponse resp = restClient.get().uri(uri)
                .retrieve().body(WxSessionResponse.class);
        if (resp == null || resp.errcode() != null && resp.errcode() != 0) {
            log.warn("wx code2session failed: {}", resp);
            throw new BusinessException(ErrorCode.WX_LOGIN_FAILED);
        }
        return new WxSession(resp.openid(), resp.unionid());
    }

    record WxSessionResponse(String openid, String unionid, Integer errcode, String errmsg) {
    }
}
