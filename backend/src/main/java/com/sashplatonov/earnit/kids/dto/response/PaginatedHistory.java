package com.sashplatonov.earnit.kids.dto.response;

import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;

import java.util.List;

public record PaginatedHistory(
    List<HistoryEntryDto> items,
    int total,
    int page,
    int limit
) { }
