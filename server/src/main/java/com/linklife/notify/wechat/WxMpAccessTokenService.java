package com.linklife.notify.wechat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.linklife.auth.wechat.WeChatProperties;
import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class WxMpAccessTokenService {

    private final WeChatProperties props;
    private final RestClient restClient;
    private final Cache<String, CachedToken> tokens = Caffeine.newBuilder()
            .expireAfter(new Expiry<String, CachedToken>() {
                private long nanos(CachedToken t) {
                    return Duration.ofSeconds(Math.max(t.expiresIn() - 300, 60)).toNanos();
                }

                @Override
                public long expireAfterCreate(String key, CachedToken t, long now) {
                    return nanos(t);
                }

                @Override
                public long expireAfterUpdate(String key, CachedToken t, long now, long current) {
                    return nanos(t);
                }

                @Override
                public long expireAfterRead(String key, CachedToken t, long now, long current) {
                    return Long.MAX_VALUE;
                }
            })
            .build();

    public WxMpAccessTokenService(WeChatProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder.build();
    }

    public String getToken() {
        CachedToken cached = tokens.get("global", k -> fetch());
        if (cached == null) {
            throw new IllegalStateException("获取微信 access_token 失败");
        }
        return cached.accessToken();
    }

    public void invalidate() {
        tokens.invalidate("global");
    }

    private CachedToken fetch() {
        URI uri = UriComponentsBuilder.fromHttpUrl(props.getApiBase())
                .path("/cgi-bin/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", props.getAppid())
                .queryParam("secret", props.getSecret())
                .build().toUri();
        TokenResponse resp = restClient.get().uri(uri).retrieve().body(TokenResponse.class);
        if (resp == null || resp.accessToken() == null || resp.accessToken().isBlank()) {
            log.warn("wx access_token fetch failed: {}", resp);
            return null;
        }
        return new CachedToken(resp.accessToken(), resp.expiresIn() == null ? 7200 : resp.expiresIn());
    }

    private record CachedToken(String accessToken, int expiresIn) {
    }

    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Integer expiresIn,
            Integer errcode, String errmsg) {
    }
}
