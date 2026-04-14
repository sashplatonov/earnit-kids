package com.sashplatonov.earnit.kids.service;

public record WebSocketSessionInfo(
    String familyId,
    Integer childId,
    String role
) {
    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
