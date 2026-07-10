package com.sashplatonov.earnit.kids.dto.response;

import java.util.List;

public record AuthPayload(
    String familyId,
    String email,
    String role,
    Integer childId,
    String childName,
    boolean isSuperAdmin,
    String permission,
    List<FamilyChoice> familyChoices,
    boolean selectionRequired
) {
    public AuthPayload {
        familyChoices = familyChoices == null ? List.of() : List.copyOf(familyChoices);
    }

    public record FamilyChoice(
        String familyId,
        String familyName,
        String permission,
        boolean blocked
    ) {}
}
