package com.sashplatonov.earnit.kids.telegram.api.response;

public record ChildTelegramConnectionResponse(
    int childId,
    boolean linked,
    Long telegramUserId
) {
}
