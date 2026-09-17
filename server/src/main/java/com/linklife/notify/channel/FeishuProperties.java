package com.linklife.notify.channel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "link.notify.feishu")
public class FeishuProperties {
    private boolean enabled;
    private String webhookUrl;
}
