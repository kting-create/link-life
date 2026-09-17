package com.linklife.notify.channel;

import com.linklife.notify.entity.Notification;

/** 按接收人逐条投递的通道（如微信订阅消息）。 */
public interface PersonalChannel {
    boolean enabled();

    void send(Notification notification);
}
