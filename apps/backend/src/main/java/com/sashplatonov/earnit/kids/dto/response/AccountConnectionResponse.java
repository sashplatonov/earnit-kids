package com.sashplatonov.earnit.kids.dto.response;

public record AccountConnectionResponse(
    String email,
    boolean emailLinked,
    boolean telegramLinked,
    String telegramUsername,
    String telegramDisplayName
) {
    public AccountConnectionResponse(String email, boolean emailLinked, boolean telegramLinked) {
        this(email, emailLinked, telegramLinked, null, null);
    }
}
