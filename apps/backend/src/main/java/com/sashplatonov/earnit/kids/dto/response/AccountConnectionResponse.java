package com.sashplatonov.earnit.kids.dto.response;

public record AccountConnectionResponse(
    String email,
    boolean emailLinked,
    boolean telegramLinked
) {
}
