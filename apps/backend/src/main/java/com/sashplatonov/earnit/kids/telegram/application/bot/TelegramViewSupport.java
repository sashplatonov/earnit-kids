package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.api.response.RequestDto;
import com.sashplatonov.earnit.kids.family.api.response.TaskDto;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class TelegramViewSupport {
    private TelegramViewSupport() {
    }

    static int pendingCount(TelegramQuickActionResponse view) {
        return pendingRequests(view).size();
    }

    static List<RequestDto> pendingRequests(TelegramQuickActionResponse view) {
        return view.requests().stream()
            .filter(request -> request.status() == PurchaseRequestStatus.pending)
            .toList();
    }

    static String requestTitle(RequestDto request, FamilyLocale locale) {
        return request.title() != null ? request.title()
            : request.taskName() != null ? request.taskName()
            : request.itemName() != null ? request.itemName()
            : new TelegramMessageResolver().text(locale, "telegram.request.request");
    }

    static int nextQueueIndex(List<RequestDto> pending, String currentRequestId) {
        if (currentRequestId == null) {
            return 0;
        }
        for (int i = 0; i < pending.size(); i++) {
            if (Long.toString(pending.get(i).id()).equals(currentRequestId)) {
                return i + 1;
            }
        }
        return 0;
    }

    static Set<Long> pendingTaskIds(TelegramQuickActionResponse view) {
        return view.requests().stream()
            .filter(request -> request.status() == PurchaseRequestStatus.pending)
            .map(RequestDto::taskId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    static List<TaskDto> orderedTasks(TelegramQuickActionResponse view) {
        Set<Long> pending = pendingTaskIds(view);
        return view.tasks().stream()
            .sorted(Comparator.comparing((TaskDto task) -> pending.contains(task.id())))
            .limit(5)
            .toList();
    }
}
