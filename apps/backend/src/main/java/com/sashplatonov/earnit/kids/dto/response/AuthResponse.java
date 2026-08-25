package com.sashplatonov.earnit.kids.dto.response;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
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
    List<FamilyChoice> familyChoices,
    FamilyLocale locale,
    boolean languageSetupRequired
) {
    public AuthResponse {
        familyChoices = familyChoices == null ? List.of() : List.copyOf(familyChoices);
    }

    public record FamilyChoice(
        String familyId,
        String familyName,
        String permission,
        boolean blocked
    ) {}

    public static AuthResponse success(String role, String familyId) {
        return success(role, familyId, null, false);
    }

    public static AuthResponse success(String role, String familyId, FamilyLocale locale, boolean languageSetupRequired) {
        return new AuthResponse(true, role, familyId, null, null, null, false, null, locale, languageSetupRequired);
    }

    public static AuthResponse childSuccess(String familyId, int childId, String childName) {
        return childSuccess(familyId, childId, childName, null, false);
    }

    public static AuthResponse childSuccess(String familyId, int childId, String childName,
                                           FamilyLocale locale, boolean languageSetupRequired) {
        return new AuthResponse(true, null, familyId, childId, childName, null, false, null,
            locale, languageSetupRequired);
    }

    public static AuthResponse selectionRequired(List<FamilyChoice> choices) {
        return new AuthResponse(true, null, null, null, null, null, true, choices, null, false);
    }

    public static AuthResponse failure(String error) {
        return new AuthResponse(false, null, null, null, null, error, false, null, null, false);
    }
}
