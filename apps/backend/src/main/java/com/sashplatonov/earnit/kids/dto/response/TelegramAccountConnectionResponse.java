package com.sashplatonov.earnit.kids.dto.response;

public record TelegramAccountConnectionResponse(
    String email,
    boolean emailConnected,
    boolean telegramConnected,
    String miniAppUrl
) { }
