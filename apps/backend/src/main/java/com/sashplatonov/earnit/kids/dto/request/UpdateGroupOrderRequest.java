package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateGroupOrderRequest(
    @NotNull(message = "{validation.section.required}")
    GroupOrderSection section,

    List<String> groups
) {
    public UpdateGroupOrderRequest {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
}
