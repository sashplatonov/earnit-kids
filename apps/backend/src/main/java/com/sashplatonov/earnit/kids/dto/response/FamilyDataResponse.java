package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FamilyDataResponse(
    int balance,
    List<TaskDto> tasks,
    List<ShopItemDto> shop,
    List<HistoryEntryDto> history,
    List<RequestDto> requests,
    List<FriendDto> friends,
    Boolean isAdmin,
    List<ChildDto> children,
    Integer lastSelectedChildId,
    String childNickname,
    Integer monthlyLimit,
    Integer dailyCoinLimit
) { }
