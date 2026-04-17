package com.sashplatonov.earnit.kids.dto.response;

public record SessionPageDataResponse(
    boolean authenticated,
    String role,
    String familyId,
    Integer childId,
    String email,
    String csrfToken
) {
    public static SessionPageDataResponse unauthenticated() {
        return new SessionPageDataResponse(false, null, null, null, null, null);
    }
}
