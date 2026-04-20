package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryEntryDto(
    Long id,
    String type,
    int amount,
    String title,
    String description,
    int moneyAmount,
    Long relatedId,
    Long taskId,
    String taskName,
    Long itemId,
    String itemName,
    String groupName,
    String comment,
    String createdAt,
    int childId
) { }
