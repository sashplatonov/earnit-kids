package com.sashplatonov.earnit.kids.family.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

public record ImportShopItemsRequest(
    @Schema(required = true) int childId,
    @NotEmpty @NotNull @Schema(required = true) List<@Valid ImportShopItemRowRequest> rows
) {
    public ImportShopItemsRequest {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
