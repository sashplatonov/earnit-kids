package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShopItemDto(
    long id,
    String name,
    int price,
    String groupName,
    Object frequency,
    String comment,
    Integer moneyLimit,
    boolean isActive,
    int childId
) { }
