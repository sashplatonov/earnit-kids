package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotNull;

public record AdjustBalanceRequest(
    @NotNull(message = "Child id is required")
    Integer childId,

    @NotNull(message = "Amount is required")
    Integer amount,

    String description
) { }