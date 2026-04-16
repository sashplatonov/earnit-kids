package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
    boolean success,
    String role,
    String familyId,
    Integer childId,
    String childName,
    String error
) {
    public static AuthResponse success(String role, String familyId) {
        return new AuthResponse(true, role, familyId, null, null, null);
    }

    public static AuthResponse childSuccess(String familyId, int childId, String childName) {
        return new AuthResponse(true, null, familyId, childId, childName, null);
    }

    public static AuthResponse failure(String error) {
        return new AuthResponse(false, null, null, null, null, error);
    }
}
