package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;

import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardMapper;
import java.util.Map;

public final class FamilyRelatedDetailsResolver {

    private FamilyRelatedDetailsResolver() {
    }

    public static HistoryDetails resolveHistoryDetails(HistoryEntryEntity entry,
                                                       Map<Long, TaskDto> taskMap,
                                                       Map<Long, ShopItemDto> shopMap,
                                                       FamilyDashboardMapper mapper) {
        if (entry.getRelatedId() == null) {
            return new HistoryDetails(entry.getDescription(), entry.getDescription(), null, null, null, null,
                entry.getGroupName(), entry.getComment());
        }

        if (entry.getType() == HistoryEntryType.earn) {
            TaskDto task = taskMap.get(entry.getRelatedId());
            if (task != null) {
                String title = mapper.firstNonBlank(entry.getDescription(), task.name());
                return new HistoryDetails(
                    title,
                    title,
                    task.id(),
                    task.name(),
                    null,
                    null,
                    mapper.firstNonBlank(entry.getGroupName(), task.groupName()),
                    mapper.firstNonBlank(entry.getComment(), task.comment())
                );
            }
        }

        if (entry.getType() == HistoryEntryType.spend) {
            ShopItemDto shopItem = shopMap.get(entry.getRelatedId());
            if (shopItem != null) {
                String title = mapper.firstNonBlank(entry.getDescription(), shopItem.name());
                return new HistoryDetails(
                    title,
                    title,
                    null,
                    null,
                    shopItem.id(),
                    shopItem.name(),
                    mapper.firstNonBlank(entry.getGroupName(), shopItem.groupName()),
                    mapper.firstNonBlank(entry.getComment(), shopItem.comment())
                );
            }
        }

        return new HistoryDetails(entry.getDescription(), entry.getDescription(), null, null, null, null,
            entry.getGroupName(), entry.getComment());
    }

    public static RequestDetails resolveRequestDetails(PurchaseRequestEntity request,
                                                       Map<Long, TaskDto> taskMap,
                                                       Map<Long, ShopItemDto> shopMap,
                                                       FamilyDashboardMapper mapper) {
        boolean purchase = isPurchaseRequest(request) || request.getItemId() != null;
        Long itemId = request.getItemId() != null ? request.getItemId() : request.getTaskId();
        if (purchase) {
            return resolvePurchaseRequestDetails(request, itemId, shopMap, mapper);
        }
        return resolveTaskRequestDetails(request, taskMap, mapper);
    }

    private static RequestDetails resolvePurchaseRequestDetails(PurchaseRequestEntity request,
                                                                Long itemId,
                                                                Map<Long, ShopItemDto> shopMap,
                                                                FamilyDashboardMapper mapper) {
        ShopItemDto shopItem = itemId != null ? shopMap.get(itemId) : null;
        String taskName = mapper.firstNonBlank(request.getTaskName(), null);
        String itemName = mapper.firstNonBlank(shopItem != null ? shopItem.name() : null, request.getTaskName());
        String title = mapper.firstNonBlank(itemName, taskName);
        String itemComment = shopItem != null ? shopItem.comment() : null;
        String itemGroup = shopItem != null ? shopItem.groupName() : null;
        return new RequestDetails(
            title,
            itemComment,
            itemGroup,
            itemComment,
            taskName,
            itemName,
            null,
            itemGroup,
            null,
            itemComment
        );
    }

    private static RequestDetails resolveTaskRequestDetails(PurchaseRequestEntity request,
                                                            Map<Long, TaskDto> taskMap,
                                                            FamilyDashboardMapper mapper) {
        TaskDto task = request.getTaskId() != null ? taskMap.get(request.getTaskId()) : null;
        String taskName = mapper.firstNonBlank(request.getTaskName(), task != null ? task.name() : null);
        String taskComment = task != null ? task.comment() : null;
        String taskGroup = task != null ? task.groupName() : null;
        return new RequestDetails(
            taskName,
            taskComment,
            taskGroup,
            taskComment,
            taskName,
            null,
            taskGroup,
            null,
            taskComment,
            null
        );
    }

    private static boolean isPurchaseRequest(PurchaseRequestEntity request) {
        return (request.getRequestType() != null && request.getRequestType().isPurchase())
            || request.getItemId() != null;
    }

    public record HistoryDetails(
        String title,
        String description,
        Long taskId,
        String taskName,
        Long itemId,
        String itemName,
        String groupName,
        String comment
    ) { }

    public record RequestDetails(
        String title,
        String description,
        String groupName,
        String comment,
        String taskName,
        String itemName,
        String taskGroup,
        String itemGroup,
        String taskComment,
        String itemComment
    ) { }
}
