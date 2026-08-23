package com.sashplatonov.earnit.kids.platform.webpush;

public interface WebPushProtocolAdapter {
    void send(WebPushSubscriptionEntity subscription, String payload) throws Exception;
}
