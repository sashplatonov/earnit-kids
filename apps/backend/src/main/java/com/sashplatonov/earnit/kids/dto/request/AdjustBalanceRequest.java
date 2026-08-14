package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdjustBalanceRequest(
    @NotNull(message = "{validation.child.id.required}")
    Integer childId,

    @NotNull(message = "{validation.amount.required}")
    @Min(value = -1_000_000, message = "{validation.amount.min}")
    @Max(value = 1_000_000, message = "{validation.amount.max}")
    Integer amount,

    String description
) { }
