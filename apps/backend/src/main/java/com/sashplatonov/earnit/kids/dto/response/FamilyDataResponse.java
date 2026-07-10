package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FamilyDataResponse(
    int balance,
    String rules,
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
) {
    public FamilyDataResponse {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        shop = shop == null ? List.of() : List.copyOf(shop);
        history = history == null ? List.of() : List.copyOf(history);
        requests = requests == null ? List.of() : List.copyOf(requests);
        friends = friends == null ? List.of() : List.copyOf(friends);
        children = children == null ? List.of() : List.copyOf(children);
    }
}
