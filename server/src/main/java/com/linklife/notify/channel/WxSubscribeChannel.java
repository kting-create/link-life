package com.linklife.notify.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.auth.wechat.WeChatProperties;
import com.linklife.notify.entity.Notification;
import com.linklife.notify.wechat.WxMpAccessTokenService;
import com.linklife.user.UserService;
import com.linklife.user.entity.User;
import java.net.URI;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class WxSubscribeChannel implements PersonalChannel {

    private static final int THING_MAX_LENGTH = 20;
    private static final int ERR_OK = 0;
    private static final int ERR_TOKEN_INVALID = 40001;
    private static final int ERR_USER_REFUSED = 43101;

    private final WxSubscribeProperties props;
    private final WxMpAccessTokenService tokenService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final String apiBase;
    private final RestClient restClient;

    public WxSubscribeChannel(WxSubscribeProperties props, WxMpAccessTokenService tokenService,
                              UserService userService, WeChatProperties weChatProps,
                              RestClient.Builder builder) {
        this.props = props;
        this.tokenService = tokenService;
        this.userService = userService;
        this.objectMapper = new ObjectMapper();
        this.apiBase = weChatProps.getApiBase();
        this.restClient = builder.build();
    }

    @Override
    public boolean enabled() {
        return props.isEnabled() && props.getTemplateId() != null && !props.getTemplateId().isBlank();
    }

    @Override
    public void send(Notification notification) {
        User user = userService.getUserById(notification.getUserId());
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            return;
        }
        sendWithRetry(user.getOpenid(), notification, true);
    }

    private void sendWithRetry(String openid, Notification notification, boolean allowRetry) {
        String page = notification.getSheetId() == null ? props.getPage()
                : props.getPage() + "?id=" + notification.getSheetId();
        Map<String, Object> body = Map.of(
                "touser", openid,
                "template_id", props.getTemplateId(),
                "page", page,
                "data", Map.of(
                        "thing1", Map.of("value", truncate(notification.getTitle())),
                        "thing2", Map.of("value", truncate(notification.getContent()))));
        URI uri = UriComponentsBuilder.fromHttpUrl(apiBase)
                .path("/cgi-bin/message/subscribe/send")
                .queryParam("access_token", tokenService.getToken())
                .build().toUri();
        String resp = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        WxSendResponse parsed = parse(resp);
        int errcode = parsed == null || parsed.errcode() == null ? -1 : parsed.errcode();
        if (errcode == ERR_OK) {
            log.debug("wx subscribe send ok: userId={}", notification.getUserId());
            return;
        }
        if (errcode == ERR_USER_REFUSED) {
            log.info("wx subscribe send skipped (user not subscribed): userId={}",
                    notification.getUserId());
            return;
        }
        if (errcode == ERR_TOKEN_INVALID && allowRetry) {
            log.info("wx access_token invalid, refresh and retry once");
            tokenService.invalidate();
            sendWithRetry(openid, notification, false);
            return;
        }
        log.warn("wx subscribe send failed: userId={}, resp={}", notification.getUserId(), resp);
    }

    private WxSendResponse parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, WxSendResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= THING_MAX_LENGTH ? value : value.substring(0, THING_MAX_LENGTH);
    }

    private record WxSendResponse(Integer errcode, String errmsg) {
    }
}
