package com.linklife.notify.channel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "link.notify.wx-subscribe")
public class WxSubscribeProperties {
    private boolean enabled;
    private String templateId;
    private String page = "pages/sheet-detail/sheet-detail";
}
