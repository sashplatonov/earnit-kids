package com.sashplatonov.earnit.kids.telegram.api.response;

import com.sashplatonov.earnit.kids.family.api.response.ChildDto;
import com.sashplatonov.earnit.kids.family.api.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.family.api.response.RequestDto;
import com.sashplatonov.earnit.kids.family.api.response.ShopItemDto;
import com.sashplatonov.earnit.kids.family.api.response.TaskDto;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;

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
    List<HistoryEntryDto> history,
    FamilyLocale locale
) {
    public TelegramQuickActionResponse(String familyId, String role, int childId, String childName,
        int balance, List<ChildDto> children, List<TaskDto> tasks, List<ShopItemDto> rewards,
        List<RequestDto> requests, List<HistoryEntryDto> history) {
        this(familyId, role, childId, childName, balance, children, tasks, rewards, requests, history,
            FamilyLocale.ru);
    }
    public TelegramQuickActionResponse {
        children = children == null ? List.of() : List.copyOf(children);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        requests = requests == null ? List.of() : List.copyOf(requests);
        history = history == null ? List.of() : List.copyOf(history);
    }
}
