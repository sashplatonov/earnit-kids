package com.sashplatonov.earnit.kids.dto.response;

import java.util.List;

public record TelegramQuickActionResponse(
    String familyId,
    String role,
    int childId,
    String childName,
    int balance,
    List<ChildDto> children,
    List<TaskDto> tasks,
    List<ShopItemDto> rewards,
    List<RequestDto> requests,
    List<HistoryEntryDto> history
) {
    public TelegramQuickActionResponse {
        children = children == null ? List.of() : List.copyOf(children);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        requests = requests == null ? List.of() : List.copyOf(requests);
        history = history == null ? List.of() : List.copyOf(history);
    }
}
