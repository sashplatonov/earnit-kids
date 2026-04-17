package com.sashplatonov.earnit.kids.dto.response;

public record AuthPayload(
    String familyId,
    String email,
    String role,
    Integer childId,
    String childName
) { }
