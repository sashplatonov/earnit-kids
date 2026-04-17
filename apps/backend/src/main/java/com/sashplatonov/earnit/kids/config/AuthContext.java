package com.sashplatonov.earnit.kids.config;

import java.util.Map;

public record AuthContext(
    String familyId,
    Integer childId,
    String role,
    String email,
    String csrfToken
) {
    public static AuthContext fromPayload(Map<String, Object> payload, String cookieCsrf) {
        String familyId = toStringValue(payload.get("familyId"));
        Integer childId = toInteger(payload.get("childId"));
        String role = toStringValue(payload.get("role"));
        String email = toStringValue(payload.get("email"));
        String csrf = cookieCsrf != null ? cookieCsrf : toStringValue(payload.get("csrfToken"));
        return new AuthContext(familyId, childId, role, email, csrf);
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    public boolean isChild() {
        return "child".equals(role);
    }

    public boolean isSuperAdmin() {
        return "super_admin".equals(role);
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
