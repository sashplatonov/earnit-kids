package com.sashplatonov.earnit.kids.family.api.response;

import java.util.List;

public record PaginatedRequests(
    List<RequestDto> items,
    int total,
    int page,
    int limit
) {
    public PaginatedRequests {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
