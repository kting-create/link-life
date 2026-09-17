package com.linklife.notify.channel;

import java.net.URI;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class FeishuChannel implements BroadcastChannel {

    private final FeishuProperties props;
    private final RestClient restClient;

    public FeishuChannel(FeishuProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder.build();
    }

    @Override
    public boolean enabled() {
        return props.isEnabled() && props.getWebhookUrl() != null && !props.getWebhookUrl().isBlank();
    }

    @Override
    public void broadcast(String summary) {
        Map<String, Object> body = Map.of(
                "msg_type", "text",
                "content", Map.of("text", "[Link-Life] " + summary));
        String resp = restClient.post()
                .uri(URI.create(props.getWebhookUrl()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        if (resp != null && !resp.contains("\"code\":0") && !resp.contains("\"StatusCode\":0")) {
            log.warn("feishu webhook non-success response: {}", resp);
        }
    }
}
