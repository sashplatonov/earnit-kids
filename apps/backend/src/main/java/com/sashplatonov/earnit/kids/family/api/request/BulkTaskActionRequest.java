package com.sashplatonov.earnit.kids.family.api.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

public record BulkTaskActionRequest(
    @Schema(required = true) int childId,
    @NotNull @Schema(required = true) BulkActionType action,
    @NotEmpty @NotNull @Schema(required = true) List<Long> taskIds,
    @Schema(required = false) String groupName
) {
    public BulkTaskActionRequest {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }
}
