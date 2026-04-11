package com.sashplatonov.earnit.kids.config;

import java.util.Map;

/**
 * Authenticated session context extracted from JWT cookie.
 * Immutable record holding identity claims for the current request.
 */
public record AuthContext(
    String familyId,
    Integer childId,
    String role,
    String email,
    String csrfToken
) {
    /** Creates an AuthContext from a JWT payload map. */
    public static AuthContext fromPayload(Map<String, Object> payload, String cookieCsrf) {
        String familyId = toStringValue(payload.get("familyId"));
        Integer childId = toInteger(payload.get("childId"));
        String role = toStringValue(payload.get("role"));
        String email = toStringValue(payload.get("email"));
        String csrf = cookieCsrf != null ? cookieCsrf : toStringValue(payload.get("csrfToken"));
        return new AuthContext(familyId, childId, role, email, csrf);
    }

    /** Returns true if the session has admin role. */
    public boolean isAdmin() {
        return "admin".equals(role);
    }

    /** Returns true if the session has child role. */
    public boolean isChild() {
        return "child".equals(role);
    }

    /** Returns true if the session has super_admin role. */
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
            } catch (NumberFormatException _) {
                return null;
            }
        }
        return null;
    }

    private static String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
