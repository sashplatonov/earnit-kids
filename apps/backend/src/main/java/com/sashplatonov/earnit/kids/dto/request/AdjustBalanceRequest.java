package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotNull;

public record AdjustBalanceRequest(
    @NotNull(message = "{validation.child.id.required}")
    Integer childId,

    @NotNull(message = "{validation.amount.required}")
    Integer amount,

    String description
) { }