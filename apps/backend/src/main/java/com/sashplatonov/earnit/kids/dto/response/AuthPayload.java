package com.sashplatonov.earnit.kids.dto.response;

import java.util.List;

public record AuthPayload(
    String familyId,
    String email,
    String role,
    Integer childId,
    String childName,
    String permission,
    List<FamilyChoice> familyChoices,
    boolean selectionRequired,
    Integer parentAccountId,
    String locale,
    boolean languageSetupRequired
) {
    public AuthPayload(String familyId, String email, String role, Integer childId, String childName,
                       boolean ignoredLegacyFlag, String permission, List<FamilyChoice> familyChoices,
                       boolean selectionRequired) {
        this(familyId, email, role, childId, childName, permission, familyChoices, selectionRequired, null, null, false);
    }

    public AuthPayload(String familyId, String email, String role, Integer childId, String childName,
                       String permission, List<FamilyChoice> familyChoices,
                       boolean selectionRequired) {
        this(familyId, email, role, childId, childName, permission,
            familyChoices, selectionRequired, null, null, false);
    }

    public AuthPayload(String familyId, String email, String role, Integer childId, String childName,
                       String permission, List<FamilyChoice> familyChoices,
                       boolean selectionRequired, Integer parentAccountId) {
        this(familyId, email, role, childId, childName, permission,
            familyChoices, selectionRequired, parentAccountId, null, false);
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
