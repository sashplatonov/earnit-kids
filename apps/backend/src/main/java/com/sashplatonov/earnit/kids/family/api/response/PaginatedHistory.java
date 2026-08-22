package com.sashplatonov.earnit.kids.family.api.response;

import java.util.List;

public record PaginatedHistory(
    List<HistoryEntryDto> items,
    int total,
    int page,
    int limit
) {
    public PaginatedHistory {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
