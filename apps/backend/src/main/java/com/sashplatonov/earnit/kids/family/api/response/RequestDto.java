package com.sashplatonov.earnit.kids.family.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestType;

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
    String note,
    int coins,
    PurchaseRequestStatus status,
    PurchaseRequestType requestType,
    int moneyAmount,
    String createdAt,
    int childId,
    String taskGroup,
    String itemGroup,
    String taskComment,
    String itemComment
) { }
