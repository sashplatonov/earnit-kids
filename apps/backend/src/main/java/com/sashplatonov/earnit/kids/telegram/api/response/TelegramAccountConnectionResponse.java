package com.sashplatonov.earnit.kids.telegram.api.response;

public record TelegramAccountConnectionResponse(
    String email,
    boolean emailConnected,
    boolean telegramConnected,
    String miniAppUrl,
    String telegramUsername,
    String telegramDisplayName
) {
    public TelegramAccountConnectionResponse(
        String email, boolean emailConnected, boolean telegramConnected, String miniAppUrl) {
        this(email, emailConnected, telegramConnected, miniAppUrl, null, null);
    }
}
