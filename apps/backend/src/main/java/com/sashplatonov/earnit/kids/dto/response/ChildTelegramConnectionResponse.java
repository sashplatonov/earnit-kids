package com.sashplatonov.earnit.kids.dto.response;

public record ChildTelegramConnectionResponse(
    int childId,
    boolean linked,
    Long telegramUserId
) {
}
