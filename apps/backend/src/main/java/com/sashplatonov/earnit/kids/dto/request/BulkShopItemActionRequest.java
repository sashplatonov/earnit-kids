package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

public record BulkShopItemActionRequest(
    @Schema(required = true) int childId,
    @NotNull @Schema(required = true) BulkActionType action,
    @NotEmpty @NotNull @Schema(required = true) List<Long> itemIds,
    @Schema(required = false) String groupName
) {
    public BulkShopItemActionRequest {
        itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
    }
}
