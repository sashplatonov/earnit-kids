package com.sashplatonov.earnit.kids.dto.response;

public record SessionPageDataResponse(
    boolean authenticated,
    String role,
    String familyId,
    Integer childId,
    String email,
    String csrfToken,
    String permission,
    String locale,
    boolean languageSetupRequired
) {
    public SessionPageDataResponse(boolean authenticated, String role, String familyId, Integer childId,
                                   String email, String csrfToken, String permission) {
        this(authenticated, role, familyId, childId, email, csrfToken, permission, null, false);
    }

    public static SessionPageDataResponse unauthenticated() {
        return new SessionPageDataResponse(false, null, null, null, null, null, null, null, false);
    }
}
