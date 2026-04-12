package com.sashplatonov.earnit.kids.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateChildSettingsRequest(
    @NotBlank(message = "Child name is required")
    @Size(max = 50, message = "Child name must be at most 50 characters")
    String name,

    @NotNull(message = "Daily coin limit is required")
    @Min(value = 0, message = "Daily coin limit must be zero or greater")
    @JsonAlias("daily_coin_limit")
    Integer dailyCoinLimit,

    @NotNull(message = "Monthly limit is required")
    @Min(value = 0, message = "Monthly limit must be zero or greater")
    @JsonAlias("monthly_limit")
    Integer monthlyLimit
) { }