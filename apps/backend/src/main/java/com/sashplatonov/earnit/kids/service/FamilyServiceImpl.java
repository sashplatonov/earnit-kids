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
import com.sashplatonov.earnit.kids.dto.request.ChildTheme;
import com.sashplatonov.earnit.kids.dto.request.FamilyPreferenceKey;
import com.sashplatonov.earnit.kids.dto.request.GroupOrderSection;
import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
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
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public final class FamilyServiceImpl implements FamilyService {
    private static final int MAX_PAGE_SIZE = 100;

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final FamilyDataRepository familyDataRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final ObjectMapper objectMapper;
    private final FamilyDashboardQueryService familyDashboardQueryService;
    private final FamilyCommandService familyCommandService;
    private final AnalyticsService analyticsService;

    @Inject
    public FamilyServiceImpl(FamilyRepository familyRepository,
                             ChildRepository childRepository,
                             FamilyDataRepository familyDataRepository,
                             HistoryRepository historyRepository,
                             TaskRepository taskRepository,
                             ShopItemRepository shopItemRepository,
                             ObjectMapper objectMapper,
                             TimeProvider timeProvider) {
        this.familyRepository = familyRepository;
        this.childRepository = childRepository;
        this.familyDataRepository = familyDataRepository;
        this.taskRepository = taskRepository;
        this.shopItemRepository = shopItemRepository;
        this.objectMapper = objectMapper;
        this.familyDashboardQueryService = new FamilyDashboardQueryServiceImpl(
            familyRepository,
            childRepository,
            familyDataRepository,
            historyRepository,
            taskRepository,
            shopItemRepository,
            objectMapper
        );
        this.familyCommandService = new FamilyCommandServiceImpl(
            familyRepository,
            childRepository,
            familyDataRepository,
            familyDashboardQueryService,
            objectMapper
        );
        this.analyticsService = new AnalyticsServiceImpl(
            familyRepository,
            historyRepository,
            taskRepository,
            shopItemRepository,
            timeProvider
        );
    }

    FamilyServiceImpl(FamilyRepository familyRepository,
                      ChildRepository childRepository,
                      FamilyDataRepository familyDataRepository,
                      HistoryRepository historyRepository,
                      TaskRepository taskRepository,
                      ShopItemRepository shopItemRepository,
                      TimeProvider timeProvider) {
        this(familyRepository, childRepository, familyDataRepository, historyRepository,
            taskRepository, shopItemRepository, new ObjectMapper(), timeProvider);
    }

    @Override
    public OperationResult<FamilyDataResponse> loadFamilyData(String familyId, Integer childId, boolean adminSession) {
        return familyDashboardQueryService.loadFamilyData(familyId, childId, adminSession);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> saveFamilyData(String familyId, Integer childId,
                                                              Map<String, Object> payload,
                                                              boolean adminSession) {
        return familyCommandService.saveFamilyData(familyId, childId, payload, adminSession);
    }

    @Override
    public OperationResult<ChildInfo> createChild(String familyId, String childName) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            log.warn("createChild failed: family not found familyId={}", familyId);
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        int familyDbId = dbIdOpt.get();

        if (childName == null || childName.isBlank()) {
            return failure("CHILD_NAME_REQUIRED", "family.childNameRequired");
        }
        if (childName.length() > 50) {
            return failure("CHILD_NAME_TOO_LONG", "family.childNameTooLong");
        }
        if (childRepository.isNicknameTaken(familyDbId, childName, null)) {
            return failure("CHILD_NAME_TAKEN", "family.childNameTaken");
        }

        Optional<ChildEntity> childOpt = childRepository.createChild(familyDbId, childName);
        if (childOpt.isEmpty()) {
            log.error("createChild failed: repository returned empty familyId={}", familyId);
            return failure("CHILD_CREATE_FAILED", "family.createFailed");
        }

        ChildEntity child = childOpt.get();
        log.info("Child created childId={} familyId={}", child.getId(), familyId);
        return OperationResult.success(new ChildInfo(child.getId(), child.getName(), child.getToken()));
    }

    @Override
    public OperationResult<Void> deleteChild(String familyId, int childId) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            log.warn("deleteChild failed: family not found familyId={} childId={}", familyId, childId);
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        var childOpt = childRepository.findByIdOptional(childId);
        if (childOpt.isEmpty() || !Objects.equals(childOpt.get().getFamilyDbId(), dbIdOpt.get())) {
            log.warn(
                "deleteChild failed: child not found or family mismatch familyId={} childId={}",
                familyId,
                childId
            );
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        childRepository.deleteChild(childId);
        log.info("Child deleted childId={} familyId={}", childId, familyId);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> updateNickname(String familyId, int childId, String newName) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        if (newName == null || newName.isBlank()) {
            return failure("NAME_REQUIRED", "family.nameRequired");
        }
        if (childRepository.isNicknameTaken(dbIdOpt.get(), newName, childId)) {
            return failure("CHILD_NAME_TAKEN", "family.childNameTaken");
        }

        childRepository.updateName(childId, newName);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> updateChildSettings(String familyId, int childId,
                                                      String name, int dailyCoinLimit,
                                                      int monthlyLimit) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }
        childRepository.updateSettings(childId, name, dailyCoinLimit, monthlyLimit);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> updateChildTheme(String familyId, int childId, ChildTheme theme) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        if (theme == null) {
            return failure("INVALID_THEME", "family.invalidTheme", Map.of("theme", "null"));
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }
        childRepository.updateTheme(childId, theme);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> updateChildGroupOrder(String familyId, int childId,
                                                       GroupOrderSection section, List<String> groups,
                                                       boolean personalOrder) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        if (section == null) {
            return failure("INVALID_GROUP_ORDER_SECTION", "family.invalidGroupOrderSection",
                Map.of("section", "null"));
        }

        String serializedOrder;
        try {
            serializedOrder = serializeGroupOrder(groups);
        } catch (JsonProcessingException ex) {
            log.warn(
                "Failed to serialize group order familyId={} childId={} section={}",
                familyId,
                childId,
                section,
                ex
            );
            return failure("GROUP_ORDER_SAVE_FAILED", "family.groupOrderSaveFailed");
        }

        childRepository.updateGroupOrder(childId, section, personalOrder, serializedOrder);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<List<FriendDto>> searchByNickname(String nickname, int excludeChildId) {
        if (nickname == null || nickname.isBlank() || nickname.trim().length() < 3) {
            return OperationResult.success(List.of());
        }

        List<FriendDto> results = childRepository.searchByNickname(nickname.trim(), excludeChildId).stream()
            .map(child -> new FriendDto(child.getId(), child.getName(), child.getBalance()))
            .toList();

        return OperationResult.success(results);
    }

    @Override
    public OperationResult<Void> addFriend(String familyId, int childId, int friendChildId) {
        if (childId == friendChildId) {
            return failure("CANNOT_ADD_SELF", "family.cannotAddSelf");
        }

        if (familyRepository.getDbId(familyId).isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        if (childRepository.findByIdOptional(friendChildId).isEmpty()) {
            return failure("USER_NOT_FOUND", "family.userNotFound");
        }

        boolean saved = familyDataRepository.addFriend(childId, friendChildId);
        if (!saved) {
            return failure("FRIEND_ADD_FAILED", "family.friendAddFailed");
        }

        return OperationResult.success(null);
    }

    @Override
    public OperationResult<List<FriendDto>> getFriendsData(int childId) {
        var friendIds = familyDataRepository.getFriendChildIds(childId);
        List<FriendDto> friends = childRepository.findByChildIds(friendIds).stream()
            .map(friend -> new FriendDto(friend.getId(), friend.getName(), friend.getBalance()))
            .toList();
        return OperationResult.success(friends);
    }

    @Override
    public OperationResult<AnalyticsResponse> getAnalyticsData(String familyId, Integer childId, String timeframe) {
        return analyticsService.getAnalyticsData(familyId, childId, timeframe);
    }

    @Override
    public OperationResult<PaginatedHistory> getHistory(String familyId, int childId, int page, int limit) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int offset = (page - 1) * effectiveLimit;
        List<HistoryEntryEntity> rows = familyDataRepository.getHistory(childId, effectiveLimit, offset);
        int total = familyDataRepository.getHistoryCount(childId);
        List<TaskDto> tasks = loadTasks(childId);
        List<ShopItemDto> shopItems = loadShopItems(childId);
        Map<Long, TaskDto> taskMap = tasks.stream()
            .collect(java.util.stream.Collectors.toMap(TaskDto::id, task -> task, (left, right) -> left));
        Map<Long, ShopItemDto> shopMap = shopItems.stream()
            .collect(java.util.stream.Collectors.toMap(ShopItemDto::id, item -> item, (left, right) -> left));
        List<HistoryEntryDto> items = rows.stream()
            .map(historyEntry -> toHistoryDto(historyEntry, taskMap, shopMap))
            .toList();
        return OperationResult.success(new PaginatedHistory(items, total, page, effectiveLimit));
    }

    @Override
    public OperationResult<PaginatedRequests> getRequests(String familyId, int page, int limit) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        int familyDbId = dbIdOpt.get();
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int offset = (page - 1) * effectiveLimit;
        List<PurchaseRequestEntity> rows = familyDataRepository.getRequests(familyDbId, effectiveLimit, offset);
        int total = familyDataRepository.getRequestsCount(familyDbId);
        Map<Long, TaskDto> taskMap = new LinkedHashMap<>();
        Map<Long, ShopItemDto> shopMap = new LinkedHashMap<>();
        rows.stream()
            .map(PurchaseRequestEntity::getChildId)
            .filter(Objects::nonNull)
            .distinct()
            .forEach(requestChildId -> {
                loadTasks(requestChildId).forEach(task -> taskMap.putIfAbsent(task.id(), task));
                loadShopItems(requestChildId).forEach(shopItem -> shopMap.putIfAbsent(shopItem.id(), shopItem));
            });
        List<RequestDto> items = rows.stream().map(request -> toRequestDto(request, taskMap, shopMap)).toList();
        return OperationResult.success(new PaginatedRequests(items, total, page, effectiveLimit));
    }

    @Override
    public OperationResult<String> getChildLoginLink(String familyId, int childId) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        var childOpt = findFamilyChild(dbIdOpt.get(), childId);
        if (childOpt.isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }
        return OperationResult.success(childOpt.get().getToken());
    }

    @Override
    public OperationResult<String> regenerateChildToken(String familyId, int childId) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        Optional<String> newToken = childRepository.regenerateToken(childId);
        if (newToken.isEmpty()) {
            return failure("TOKEN_GENERATION_FAILED", "family.tokenGenerationFailed");
        }
        return OperationResult.success(newToken.get());
    }

    @Override
    public OperationResult<Void> updatePreference(String familyId, FamilyPreferenceKey key, Object value) {
        if (key == FamilyPreferenceKey.lastSelectedChildId) {
            Optional<Integer> familyDbIdOpt = familyRepository.getDbId(familyId);
            if (familyDbIdOpt.isEmpty()) {
                return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
            }

            Integer childId = parseChildIdPreference(value);
            if (value != null && childId == null) {
                return failure("INVALID_CHILD_ID", "family.invalidChildId");
            }
            if (childId != null) {
                Optional<ChildEntity> childOpt = childRepository.findByIdOptional(childId);
                if (childOpt.isEmpty() || !Objects.equals(childOpt.get().getFamilyDbId(), familyDbIdOpt.get())) {
                    return failure("CHILD_NOT_FOUND", "family.childNotFound");
                }
            }

            familyRepository.updateLastSelectedChild(familyId, childId);
            return OperationResult.success(null);
        }
        return failure("UNKNOWN_SETTING", "family.unknownSetting", Map.of("key", String.valueOf(key)));
    }

    private static <T> OperationResult<T> failure(String errorCode, String messageKey) {
        return OperationResult.failure(errorCode, BackendMessages.message(messageKey));
    }

    private static <T> OperationResult<T> failure(String errorCode, String messageKey, Map<String, String> variables) {
        return OperationResult.failure(errorCode, BackendMessages.message(messageKey, variables));
    }

    private Integer parseChildIdPreference(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String serializeGroupOrder(List<String> groups) throws JsonProcessingException {
        List<String> normalizedGroups = normalizeGroupOrder(groups);
        if (normalizedGroups.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(normalizedGroups);
    }

    private List<String> normalizeGroupOrder(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String group : groups) {
            if (group == null) {
                continue;
            }

            String trimmed = group.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }

        return List.copyOf(normalized);
    }

    private Optional<ChildEntity> findFamilyChild(int familyDbId, int childId) {
        return childRepository.findByIdOptional(childId)
            .filter(child -> Objects.equals(child.getFamilyDbId(), familyDbId));
    }

    private List<TaskDto> loadTasks(int childId) {
        return loadTasks(childId, Map.of());
    }

    private List<TaskDto> loadTasks(int childId, Map<Long, String> lastCompletedAtByTaskId) {
        return familyDataRepository.getTasks(childId).stream()
            .map(task -> toTaskDto(task, lastCompletedAtByTaskId.get(task.getTaskId())))
            .toList();
    }

    private List<ShopItemDto> loadShopItems(int childId) {
        return loadShopItems(childId, Map.of());
    }

    private List<ShopItemDto> loadShopItems(int childId, Map<Long, String> lastPurchasedAtByItemId) {
        return familyDataRepository.getShopItems(childId).stream()
            .map(shopItem -> toShopItemDto(shopItem, lastPurchasedAtByItemId.get(shopItem.getItemId())))
            .toList();
    }

    private JsonNode parseFrequency(JsonNode rawFrequency) {
        if (rawFrequency == null || rawFrequency.isNull()) {
            return null;
        }

        if (rawFrequency.isTextual()) {
            String value = rawFrequency.asText();
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readTree(value);
            } catch (Exception ex) {
                log.debug("Failed to parse stored frequency JSON text node: {}", value, ex);
                return rawFrequency;
            }
        }

        return rawFrequency;
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

    private HistoryEntryDto toHistoryDto(HistoryEntryEntity entry,
                                         Map<Long, TaskDto> taskMap,
                                         Map<Long, ShopItemDto> shopMap) {
        HistoryDetails details = enrichHistoryDetails(entry, taskMap, shopMap);
        return new HistoryEntryDto(entry.getExternalId(), entry.getType(), entry.getAmount(),
            details.title(),
            details.description(), entry.getMoneyAmount(), entry.getRelatedId(), details.taskId(),
            details.taskName(), details.itemId(), details.itemName(), details.groupName(), details.comment(),
            entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null,
            entry.getChildId());
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
                shopItem = findShopItemDto(
                    entry.getFamilyId(),
                    entry.getChildId(),
                    entry.getRelatedId()
                );
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean isPurchaseRequest(PurchaseRequestEntity request) {
        return (request.getRequestType() != null && request.getRequestType().isPurchase())
            || request.getItemId() != null;
    }

    private RequestDto toRequestDto(PurchaseRequestEntity request,
                                    Map<Long, TaskDto> taskMap,
                                    Map<Long, ShopItemDto> shopMap) {
        RequestDetails details = enrichRequestDetails(request, taskMap, shopMap);
        return new RequestDto(request.getId(), request.getTaskId(), details.taskName(),
            request.getItemId(), details.itemName(), details.title(), details.description(),
            details.groupName(),
            details.comment(),
            request.getNote(),
            request.getCoins(),
            request.getStatus(),
            request.getRequestType(),
            request.getMoneyAmount(), request.getCreatedAt() != null ? request.getCreatedAt().toString() : null,
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
        return query.firstResultOptional().map(shopItem -> toShopItemDto(shopItem, null)).orElse(null);
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
