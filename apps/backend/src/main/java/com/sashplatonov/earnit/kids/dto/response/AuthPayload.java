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
    boolean selectionRequired,
    Integer parentAccountId
) {
    public AuthPayload(String familyId, String email, String role, Integer childId, String childName,
                       boolean isSuperAdmin, String permission, List<FamilyChoice> familyChoices,
                       boolean selectionRequired) {
        this(familyId, email, role, childId, childName, isSuperAdmin, permission,
            familyChoices, selectionRequired, null);
    }
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
