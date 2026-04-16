package com.sashplatonov.earnit.kids.dto.response;

public record WebSocketEventResponse(
    String type,
    Object data,
    String timestamp
) {
}
