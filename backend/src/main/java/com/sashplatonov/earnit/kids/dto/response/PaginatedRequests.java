package com.sashplatonov.earnit.kids.dto.response;

import java.util.List;

public record PaginatedRequests(
    List<RequestDto> items,
    int total,
    int page,
    int limit
) { }
