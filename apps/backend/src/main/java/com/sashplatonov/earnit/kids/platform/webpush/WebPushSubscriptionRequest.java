package com.sashplatonov.earnit.kids.platform.webpush;

public record WebPushSubscriptionRequest(String endpoint, String p256dh, String auth) {}
