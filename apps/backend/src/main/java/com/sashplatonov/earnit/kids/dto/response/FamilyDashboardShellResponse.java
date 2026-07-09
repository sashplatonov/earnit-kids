package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FamilyDashboardShellResponse(
    int balance,
    String rules,
    List<TaskDto> tasks,
    List<ShopItemDto> shop,
    Boolean isAdmin,
    List<ChildDto> children,
    Integer lastSelectedChildId,
    Integer activeChildId,
    String childNickname,
    Integer monthlyLimit,
    Integer dailyCoinLimit
) { }
