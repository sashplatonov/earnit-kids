package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.ChildDto;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyDataRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyDashboardQueryServiceImpl implements FamilyDashboardQueryService {

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final FamilyDataRepository familyDataRepository;
    private final HistoryRepository historyRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final ObjectMapper objectMapper;

    @Override
    public OperationResult<FamilyDataResponse> loadFamilyData(String familyId, Integer childId, boolean adminSession) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        int familyDbId = dbIdOpt.get();
        String rules = familyRepository.getRules(familyId).orElse(null);
        Integer persistedChildId = familyRepository.getLastSelectedChildId(familyId).orElse(null);

        List<ChildEntity> children = childRepository.getChildren(familyDbId);
        if (children.isEmpty()) {
            return OperationResult.success(emptyFamilyDataResponse(rules, adminSession));
        }

        List<ChildEntity> visibleChildren = resolveVisibleChildren(children, adminSession, childId);
        if (visibleChildren.isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        ChildEntity activeChild = resolveActiveChild(visibleChildren, childId, persistedChildId, adminSession);
        Integer resolvedLastSelectedChildId = resolveLastSelectedChildId(
            children,
            activeChild,
            persistedChildId,
            adminSession
        );

        Map<Long, String> lastCompletedAtByTaskId = loadLatestHistoryTimestamps(
            activeChild.getId(),
            HistoryEntryType.earn
        );
        Map<Long, String> lastPurchasedAtByItemId = loadLatestHistoryTimestamps(
            activeChild.getId(),
            HistoryEntryType.spend
        );
        List<TaskDto> tasks = loadTasks(activeChild.getId(), lastCompletedAtByTaskId);
        List<ShopItemDto> shopItems = loadShopItems(activeChild.getId(), lastPurchasedAtByItemId);

        return OperationResult.success(buildFamilyDataResponse(
            activeChild,
            rules,
            adminSession,
            tasks,
            shopItems,
            familyDbId,
            resolvedLastSelectedChildId,
            visibleChildren
        ));
    }

    private FamilyDataResponse buildFamilyDataResponse(
        ChildEntity activeChild,
        String rules,
        boolean adminSession,
        List<TaskDto> tasks,
        List<ShopItemDto> shopItems,
        int familyDbId,
        Integer resolvedLastSelectedChildId,
        List<ChildEntity> visibleChildren
    ) {
        Map<Long, TaskDto> taskMap = tasks.stream()
            .collect(java.util.stream.Collectors.toMap(TaskDto::id, task -> task, (left, right) -> left));
        Map<Long, ShopItemDto> shopMap = shopItems.stream()
            .collect(java.util.stream.Collectors.toMap(ShopItemDto::id, item -> item, (left, right) -> left));

        List<HistoryEntryDto> history = loadHistory(activeChild.getId(), taskMap, shopMap);
        List<RequestDto> requests = loadRequests(familyDbId, activeChild.getId(), adminSession, taskMap, shopMap);
        List<FriendDto> friends = loadFriends(activeChild.getId());
        List<ChildDto> childDtos = visibleChildren.stream().map(this::toChildDto).toList();

        return new FamilyDataResponse(
            activeChild.getBalance(),
            rules,
            tasks,
            shopItems,
            history,
            requests,
            friends,
            adminSession ? Boolean.TRUE : null,
            childDtos,
            resolvedLastSelectedChildId,
            activeChild.getName(),
            activeChild.getMonthlyLimit(),
            activeChild.getDailyCoinLimit()
        );
    }

    private FamilyDataResponse emptyFamilyDataResponse(String rules, boolean adminSession) {
        return new FamilyDataResponse(
            0,
            rules,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            adminSession ? Boolean.TRUE : null,
            List.of(),
            null,
            null,
            null,
            null
        );
    }

    private List<ChildEntity> resolveVisibleChildren(List<ChildEntity> children,
                                                     boolean adminSession,
                                                     Integer childId) {
        if (adminSession) {
            return children;
        }
        if (childId == null) {
            return List.of();
        }
        return children.stream()
            .filter(child -> Objects.equals(child.getId(), childId))
            .toList();
    }

    private ChildEntity resolveActiveChild(List<ChildEntity> visibleChildren,
                                           Integer requestedChildId,
                                           Integer persistedChildId,
                                           boolean adminSession) {
        Integer preferredChildId = adminSession
            ? (requestedChildId != null ? requestedChildId : persistedChildId)
            : visibleChildren.getFirst().getId();
        if (preferredChildId == null) {
            return visibleChildren.getFirst();
        }
        return visibleChildren.stream()
            .filter(child -> Objects.equals(child.getId(), preferredChildId))
            .findFirst()
            .orElse(visibleChildren.getFirst());
    }

    private Integer resolveLastSelectedChildId(List<ChildEntity> children,
                                               ChildEntity activeChild,
                                               Integer persistedChildId,
                                               boolean adminSession) {
        if (!adminSession) {
            return activeChild.getId();
        }
        return children.stream()
            .map(ChildEntity::getId)
            .filter(id -> Objects.equals(id, persistedChildId))
            .findFirst()
            .orElse(activeChild.getId());
    }

    private List<HistoryEntryDto> loadHistory(int childId, Map<Long, TaskDto> taskMap, Map<Long, ShopItemDto> shopMap) {
        return familyDataRepository.getHistory(childId, 50, 0).stream()
            .map(historyEntry -> toHistoryDto(historyEntry, taskMap, shopMap))
            .toList();
    }

    private List<RequestDto> loadRequests(int familyDbId,
                                          int activeChildId,
                                          boolean adminSession,
                                          Map<Long, TaskDto> taskMap,
                                          Map<Long, ShopItemDto> shopMap) {
        return familyDataRepository.getRequests(familyDbId, 50, 0).stream()
            .filter(request -> adminSession || Objects.equals(request.getChildId(), activeChildId))
            .map(request -> toRequestDto(
                request,
                Objects.equals(request.getChildId(), activeChildId) ? taskMap : Map.of(),
                Objects.equals(request.getChildId(), activeChildId) ? shopMap : Map.of()
            ))
            .toList();
    }

    private List<FriendDto> loadFriends(int childId) {
        var friendIds = familyDataRepository.getFriendChildIds(childId);
        return childRepository.findByChildIds(friendIds).stream()
            .map(friend -> new FriendDto(friend.getId(), friend.getName(), friend.getBalance()))
            .toList();
    }

    private List<TaskDto> loadTasks(int childId, Map<Long, String> lastCompletedAtByTaskId) {
        return familyDataRepository.getTasks(childId).stream()
            .map(task -> toTaskDto(task, lastCompletedAtByTaskId.get(task.getTaskId())))
            .toList();
    }

    private List<ShopItemDto> loadShopItems(int childId, Map<Long, String> lastPurchasedAtByItemId) {
        return familyDataRepository.getShopItems(childId).stream()
            .map(shopItem -> toShopItemDto(shopItem, lastPurchasedAtByItemId.get(shopItem.getItemId())))
            .toList();
    }

    private Map<Long, String> loadLatestHistoryTimestamps(int childId, HistoryEntryType type) {
        Map<Long, Instant> aggregated = historyRepository.loadLatestTimestampsByRelatedId(childId, type);
        Map<Long, String> latestTimestamps = new LinkedHashMap<>();
        if (aggregated != null && !aggregated.isEmpty()) {
            aggregated.forEach((id, instant) -> latestTimestamps.put(id, instant.toString()));
            return latestTimestamps;
        }

        historyRepository.list(
            "childId = ?1 AND type = ?2 AND relatedId IS NOT NULL ORDER BY createdAt DESC, id DESC",
            childId,
            type
        ).stream()
            .filter(entry -> entry.getRelatedId() != null && entry.getCreatedAt() != null)
            .forEach(entry -> latestTimestamps.putIfAbsent(entry.getRelatedId(), entry.getCreatedAt().toString()));
        return latestTimestamps;
    }

    private ChildDto toChildDto(ChildEntity child) {
        return new ChildDto(
            child.getId(),
            child.getName(),
            child.getBalance(),
            child.getMonthlyLimit(),
            child.getDailyCoinLimit(),
            child.getTheme(),
            parseGroupOrder(child.getTaskGroupOrder()),
            parseGroupOrder(child.getShopGroupOrder()),
            parseGroupOrder(child.getChildTaskGroupOrder()),
            parseGroupOrder(child.getChildShopGroupOrder())
        );
    }

    private TaskDto toTaskDto(TaskEntity task, String lastCompletedAt) {
        return new TaskDto(
            task.getTaskId(),
            task.getName(),
            task.getCoins(),
            task.getGroupName(),
            parseFrequency(task.getFrequency()),
            task.getComment(),
            task.getMoneyLimit(),
            task.isActive(),
            task.getChildId(),
            lastCompletedAt
        );
    }

    private ShopItemDto toShopItemDto(ShopItemEntity shopItem, String lastPurchasedAt) {
        return new ShopItemDto(
            shopItem.getItemId(),
            shopItem.getName(),
            shopItem.getPrice(),
            shopItem.getGroupName(),
            parseFrequency(shopItem.getFrequency()),
            shopItem.getComment(),
            shopItem.getMoneyLimit(),
            shopItem.isActive(),
            shopItem.getChildId(),
            lastPurchasedAt
        );
    }

    private List<String> parseGroupOrder(String rawGroupOrder) {
        if (rawGroupOrder == null || rawGroupOrder.isBlank()) {
            return List.of();
        }

        try {
            JsonNode node = objectMapper.readTree(rawGroupOrder);
            if (!node.isArray()) {
                return List.of();
            }

            List<String> groups = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isTextual()) {
                    continue;
                }

                String value = item.asText().trim();
                if (!value.isEmpty() && !groups.contains(value)) {
                    groups.add(value);
                }
            }

            return List.copyOf(groups);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Object parseFrequency(JsonNode rawFrequency) {
        if (rawFrequency == null || rawFrequency.isNull()) {
            return null;
        }

        if (rawFrequency.isTextual()) {
            String value = rawFrequency.asText();
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readValue(value, Object.class);
            } catch (Exception ex) {
                return value;
            }
        }

        return objectMapper.convertValue(rawFrequency, Object.class);
    }

    private HistoryEntryDto toHistoryDto(HistoryEntryEntity entry,
                                         Map<Long, TaskDto> taskMap,
                                         Map<Long, ShopItemDto> shopMap) {
        HistoryDetails details = enrichHistoryDetails(entry, taskMap, shopMap);
        return new HistoryEntryDto(
            entry.getExternalId(),
            entry.getType(),
            entry.getAmount(),
            details.title(),
            details.description(),
            entry.getMoneyAmount(),
            entry.getRelatedId(),
            details.taskId(),
            details.taskName(),
            details.itemId(),
            details.itemName(),
            details.groupName(),
            details.comment(),
            entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null,
            entry.getChildId()
        );
    }

    private HistoryDetails enrichHistoryDetails(HistoryEntryEntity entry,
                                                Map<Long, TaskDto> taskMap,
                                                Map<Long, ShopItemDto> shopMap) {
        if (entry.getRelatedId() == null) {
            return new HistoryDetails(entry.getDescription(), entry.getDescription(), null, null, null, null,
                entry.getGroupName(), entry.getComment());
        }

        if (entry.getType() == HistoryEntryType.earn) {
            TaskDto task = taskMap.get(entry.getRelatedId());
            if (task == null) {
                task = findTaskDto(entry.getFamilyId(), entry.getChildId(), entry.getRelatedId());
            }
            if (task != null) {
                String title = firstNonBlank(entry.getDescription(), task.name());
                return new HistoryDetails(
                    title,
                    title,
                    task.id(),
                    task.name(),
                    null,
                    null,
                    firstNonBlank(entry.getGroupName(), task.groupName()),
                    firstNonBlank(entry.getComment(), task.comment())
                );
            }
        }

        if (entry.getType() == HistoryEntryType.spend) {
            ShopItemDto shopItem = shopMap.get(entry.getRelatedId());
            if (shopItem == null) {
                shopItem = findShopItemDto(entry.getFamilyId(), entry.getChildId(), entry.getRelatedId());
            }
            if (shopItem != null) {
                String title = firstNonBlank(entry.getDescription(), shopItem.name());
                return new HistoryDetails(
                    title,
                    title,
                    null,
                    null,
                    shopItem.id(),
                    shopItem.name(),
                    firstNonBlank(entry.getGroupName(), shopItem.groupName()),
                    firstNonBlank(entry.getComment(), shopItem.comment())
                );
            }
        }

        return new HistoryDetails(entry.getDescription(), entry.getDescription(), null, null, null, null,
            entry.getGroupName(), entry.getComment());
    }

    private RequestDto toRequestDto(PurchaseRequestEntity request,
                                    Map<Long, TaskDto> taskMap,
                                    Map<Long, ShopItemDto> shopMap) {
        RequestDetails details = enrichRequestDetails(request, taskMap, shopMap);
        return new RequestDto(
            request.getId(),
            request.getTaskId(),
            details.taskName(),
            request.getItemId(),
            details.itemName(),
            details.title(),
            details.description(),
            details.groupName(),
            details.comment(),
            request.getNote(),
            request.getCoins(),
            request.getStatus(),
            request.getRequestType(),
            request.getMoneyAmount(),
            request.getCreatedAt() != null ? request.getCreatedAt().toString() : null,
            request.getChildId(),
            details.taskGroup(),
            details.itemGroup(),
            details.taskComment(),
            details.itemComment()
        );
    }

    private RequestDetails enrichRequestDetails(PurchaseRequestEntity request,
                                                Map<Long, TaskDto> taskMap,
                                                Map<Long, ShopItemDto> shopMap) {
        boolean purchase = isPurchaseRequest(request) || request.getItemId() != null;
        Long itemId = request.getItemId() != null ? request.getItemId() : request.getTaskId();
        ShopItemDto shopItem = null;
        TaskDto task = null;

        if (purchase && itemId != null) {
            shopItem = shopMap.get(itemId);
            if (shopItem == null) {
                shopItem = findShopItemDto(request.getFamilyId(), request.getChildId(), itemId);
            }
        } else if (request.getTaskId() != null) {
            task = taskMap.get(request.getTaskId());
            if (task == null) {
                task = findTaskDto(request.getFamilyId(), request.getChildId(), request.getTaskId());
            }
        }

        String taskName = firstNonBlank(request.getTaskName(), task != null ? task.name() : null);
        String itemName = purchase
            ? firstNonBlank(shopItem != null ? shopItem.name() : null, request.getTaskName())
            : null;
        String title = purchase ? firstNonBlank(itemName, taskName) : taskName;
        String taskComment = task != null ? task.comment() : null;
        String itemComment = shopItem != null ? shopItem.comment() : null;
        String taskGroup = task != null ? task.groupName() : null;
        String itemGroup = shopItem != null ? shopItem.groupName() : null;
        String description = purchase ? itemComment : taskComment;
        String groupName = purchase ? itemGroup : taskGroup;

        return new RequestDetails(title, description, groupName, description, taskName, itemName,
            taskGroup, itemGroup, taskComment, itemComment);
    }

    private TaskDto findTaskDto(int familyDbId, int childId, Long taskId) {
        if (taskId == null) {
            return null;
        }
        var query = taskRepository.find(
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 ORDER BY id DESC",
            familyDbId,
            childId,
            taskId
        );
        if (query == null) {
            return null;
        }
        return query.firstResultOptional().map(task -> toTaskDto(task, null)).orElse(null);
    }

    private ShopItemDto findShopItemDto(int familyDbId, int childId, Long itemId) {
        if (itemId == null) {
            return null;
        }
        var query = shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 ORDER BY id DESC",
            familyDbId,
            childId,
            itemId
        );
        if (query == null) {
            return null;
        }
        return query.firstResultOptional().map(item -> toShopItemDto(item, null)).orElse(null);
    }

    private boolean isPurchaseRequest(PurchaseRequestEntity request) {
        return (request.getRequestType() != null && request.getRequestType().isPurchase())
            || request.getItemId() != null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static <T> OperationResult<T> failure(String errorCode, String messageKey) {
        return OperationResult.failure(errorCode, BackendMessages.message(messageKey));
    }

    private record HistoryDetails(
        String title,
        String description,
        Long taskId,
        String taskName,
        Long itemId,
        String itemName,
        String groupName,
        String comment
    ) { }

    private record RequestDetails(
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
