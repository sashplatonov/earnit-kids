package com.sashplatonov.earnit.kids.family.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record ImportTaskRowRequest(
    @Schema(required = true) int rowNumber,
    @NotBlank @Schema(required = true) String title,
    @NotNull @Schema(required = true) Integer coins,
    @Schema(required = false) String groupName,
    @Schema(required = false) String comment,
    @Schema(required = false) Integer frequencyLimit,
    @Schema(required = false) FrequencyPeriod frequencyPeriod,
    @Schema(required = false) Integer moneyLimit,
    @Schema(required = false) String icon,
    @Schema(required = false) Boolean isActive
) { }
