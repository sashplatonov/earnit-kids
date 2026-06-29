package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record ImportShopItemRowRequest(
    @Schema(required = true) int rowNumber,
    @NotBlank @Schema(required = true) String name,
    @NotNull @Schema(required = true) Integer price,
    @Schema(required = false) String groupName,
    @Schema(required = false) String comment,
    @Schema(required = false) Integer moneyLimit,
    @Schema(required = false) ShopItemImportType type,
    @Schema(required = false) Boolean isActive
) { }
