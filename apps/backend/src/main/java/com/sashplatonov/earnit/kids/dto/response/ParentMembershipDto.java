package com.sashplatonov.earnit.kids.dto.response;

public record ParentMembershipDto(
    Integer id,
    String email,
    String permission,
    String status
) {}