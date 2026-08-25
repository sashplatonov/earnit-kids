package com.sashplatonov.earnit.kids.telegram.application.bot;

record TelegramParentRequestDetails(
    boolean approved,
    long requestId,
    int childId,
    long telegramUserId,
    boolean queueContext,
    String retryData) {}
