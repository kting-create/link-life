package com.linklife.notify.channel;

/** 全局广播通道（如飞书群 Webhook），每个事件发一条摘要。 */
public interface BroadcastChannel {
    boolean enabled();

    void broadcast(String summary);
}
