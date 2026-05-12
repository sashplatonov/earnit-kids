package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
    boolean success,
    String role,
    String familyId,
    Integer childId,
    String childName,
    String error,
    boolean selectionRequired,
    List<FamilyChoice> familyChoices
) {
    public record FamilyChoice(
        String familyId,
        String familyName,
        String permission
    ) {}

    public static AuthResponse success(String role, String familyId) {
        return new AuthResponse(true, role, familyId, null, null, null, false, null);
    }

    public static AuthResponse childSuccess(String familyId, int childId, String childName) {
        return new AuthResponse(true, null, familyId, childId, childName, null, false, null);
    }

    public static AuthResponse selectionRequired(List<FamilyChoice> choices) {
        return new AuthResponse(true, null, null, null, null, null, true, choices);
    }

    public static AuthResponse failure(String error) {
        return new AuthResponse(false, null, null, null, null, error, false, null);
    }
}
