package com.sashplatonov.earnit.kids.telegram.api.response;

public record TelegramAccountConnectionResponse(
    String email,
    boolean emailConnected,
    boolean telegramConnected,
    String miniAppUrl
) { }
