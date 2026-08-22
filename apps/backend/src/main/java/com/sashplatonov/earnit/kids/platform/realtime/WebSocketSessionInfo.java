package com.sashplatonov.earnit.kids.platform.realtime;

public record WebSocketSessionInfo(
    String familyId,
    Integer childId,
    String role
) {
    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
