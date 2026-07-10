package com.sashplatonov.earnit.kids.config.auth;

import java.util.Map;

public record AuthContext(
    String familyId,
    Integer childId,
    String role,
    String email,
    String csrfToken,
    boolean isSuperAdmin,
    String permission
) {
    public static AuthContext fromPayload(Map<String, Object> payload, String cookieCsrf) {
        String familyId = toStringValue(payload.get("familyId"));
        Integer childId = toInteger(payload.get("childId"));
        String role = toStringValue(payload.get("role"));
        String email = toStringValue(payload.get("email"));
        String csrf = cookieCsrf != null ? cookieCsrf : toStringValue(payload.get("csrfToken"));
        boolean isSuperAdmin = Boolean.TRUE.equals(payload.get("isSuperAdmin"));
        String permission = toStringValue(payload.get("permission"));
        return new AuthContext(familyId, childId, role, email, csrf, isSuperAdmin, permission);
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    public boolean isChild() {
        return "child".equals(role);
    }

    public boolean isSuperAdmin() {
        return isSuperAdmin;
    }

    public boolean isFamilyAdmin() {
        return "family_admin".equals(permission);
    }

    public boolean isEditor() {
        return "editor".equals(permission);
    }

    public boolean isViewer() {
        return "viewer".equals(permission);
    }

    public boolean canEditFamilyData() {
        return isSuperAdmin || isFamilyAdmin() || isEditor();
    }

    public boolean canManageMemberships() {
        return isSuperAdmin || isFamilyAdmin();
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
