package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateGroupOrderRequest(
    @NotNull(message = "{validation.section.required}")
    GroupOrderSection section,

    List<String> groups,

    List<String> hiddenGroups
) {
    public UpdateGroupOrderRequest {
        groups = groups == null ? List.of() : List.copyOf(groups);
        hiddenGroups = hiddenGroups == null ? List.of() : List.copyOf(hiddenGroups);
    }
}
