package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryEntryDto(
    Long id,
    String type,
    int amount,
    String description,
    int moneyAmount,
    Long relatedId,
    String groupName,
    String comment,
    String createdAt,
    int childId
) { }
