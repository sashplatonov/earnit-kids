package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestDto(
    long id,
    Long taskId,
    String taskName,
    Long itemId,
    String itemName,
    String title,
    String description,
    String groupName,
    String comment,
    int coins,
    String status,
    String requestType,
    int moneyAmount,
    String createdAt,
    int childId,
    String taskGroup,
    String itemGroup,
    String taskComment,
    String itemComment
) { }
