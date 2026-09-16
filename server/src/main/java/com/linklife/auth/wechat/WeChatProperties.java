package com.linklife.auth.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "link.wx")
public class WeChatProperties {
    private String appid;
    private String secret;
    private String apiBase = "https://api.weixin.qq.com";
}
