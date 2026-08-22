package com.sashplatonov.earnit.kids.shared.api.response;

public record WebSocketEventResponse(
    String type,
    Object data,
    String timestamp
) {
}
