package com.sashplatonov.earnit.kids.dto.response;

import java.util.List;

public record PaginatedHistory(
    List<HistoryEntryDto> items,
    int total,
    int page,
    int limit
) { }
