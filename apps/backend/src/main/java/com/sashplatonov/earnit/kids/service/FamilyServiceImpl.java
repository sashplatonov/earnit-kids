package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sashplatonov.earnit.kids.dto.request.ChildTheme;
import com.sashplatonov.earnit.kids.dto.request.FamilyPreferenceKey;
import com.sashplatonov.earnit.kids.dto.request.GroupOrderSection;
import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import com.sashplatonov.earnit.kids.dto.response.ChildDto;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyDataRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemUpsertCommand;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskUpsertCommand;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
@Slf4j
public final class FamilyServiceImpl implements FamilyService {
    private static final int MAX_PAGE_SIZE = 100;

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final FamilyDataRepository familyDataRepository;
    private final HistoryRepository historyRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final ObjectMapper objectMapper;
    private final TimeProvider timeProvider;

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
        this.historyRepository = historyRepository;
        this.taskRepository = taskRepository;
        this.shopItemRepository = shopItemRepository;
        this.objectMapper = objectMapper;
        this.timeProvider = timeProvider;
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
            activeChild, rules, adminSession, tasks, shopItems,
            familyDbId, resolvedLastSelectedChildId, visibleChildren
        ));
    }

    private FamilyDataResponse buildFamilyDataResponse(
            ChildEntity activeChild, String rules, boolean adminSession,
            List<TaskDto> tasks, List<ShopItemDto> shopItems,
            int familyDbId, Integer resolvedLastSelectedChildId,
            List<ChildEntity> visibleChildren) {
        Map<Long, TaskDto> taskMap = tasks.stream()
            .collect(java.util.stream.Collectors.toMap(TaskDto::id, t -> t, (a, b) -> a));
        Map<Long, ShopItemDto> shopMap = shopItems.stream()
            .collect(java.util.stream.Collectors.toMap(ShopItemDto::id, s -> s, (a, b) -> a));

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

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> saveFamilyData(String familyId, Integer childId,
                                                              Map<String, Object> payload,
                                                              boolean adminSession) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        int familyDbId = dbIdOpt.get();
        List<ChildEntity> children = childRepository.getChildren(familyDbId);
        if (children.isEmpty()) {
            return loadFamilyData(familyId, childId, adminSession);
        }

        List<ChildEntity> accessibleChildren = resolveVisibleChildren(children, adminSession, childId);
        if (accessibleChildren.isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        Integer selectedChildId = resolveSelectedChildId(familyId, childId, payload, accessibleChildren, adminSession);
        if (selectedChildId != null
            && accessibleChildren.stream().noneMatch(child -> Objects.equals(child.getId(), selectedChildId))) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        syncFamilyRules(familyId, payload, adminSession);

        syncBalances(familyDbId, selectedChildId, payload, accessibleChildren);
        syncTasks(familyDbId, selectedChildId, payload);
        syncShopItems(familyDbId, selectedChildId, payload);
        familyRepository.updateLastActivity(familyId);

        return loadFamilyData(familyId, selectedChildId, adminSession);
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
        Optional<Integer> familyDbIdOpt = familyRepository.getDbId(familyId);
        if (familyDbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        int familyDbId = familyDbIdOpt.get();
        Duration periodDuration = resolveTimeframeDuration(timeframe);
        Instant now = timeProvider.now();
        Instant periodStart = now.minus(periodDuration);
        Instant previousStart = periodStart.minus(periodDuration);

        // EXPLAIN: Use SQL aggregation instead of loading full history rows
        var currentRaw = historyRepository.summarizePeriod(familyDbId, childId, periodStart, now);
        var previousRaw = historyRepository.summarizePeriod(familyDbId, childId, previousStart, periodStart);

        int[] currentSummary = normalizeSummary(currentRaw);
        int[] previousSummary = normalizeSummary(previousRaw);

        int currentNet = currentSummary[0] - currentSummary[1];
        int previousNet = previousSummary[0] - previousSummary[1];

        var summary = new AnalyticsResponse.AnalyticsSummary(currentSummary[0], currentSummary[1], currentNet);
        var comparison = new AnalyticsResponse.AnalyticsSummary(previousSummary[0], previousSummary[1], previousNet);

        List<TaskEntity> tasks = queryTasks(familyDbId, childId);
        List<ShopItemEntity> items = queryShopItems(familyDbId, childId);

        List<AnalyticsResponse.AnalyticsStatItem> topTasks = buildTopTaskStatsAggregated(
            historyRepository.topTasksInPeriod(familyDbId, childId, periodStart, now), tasks);
        List<AnalyticsResponse.AnalyticsStatItem> topItems = buildTopItemStatsAggregated(
            historyRepository.topItemsInPeriod(familyDbId, childId, periodStart, now), items);

        List<AnalyticsResponse.AnalyticsTrendPoint> trends = buildTrendsAggregated(
            historyRepository.dailyTrendInPeriod(familyDbId, childId, periodStart, now));

        List<AnalyticsResponse.AnalyticsRecommendation> recommendations = buildRecommendations(familyDbId, childId);

        return OperationResult.success(new AnalyticsResponse(
            summary,
            topTasks,
            topItems,
            trends,
            comparison,
            recommendations
        ));
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
            .collect(java.util.stream.Collectors.toMap(TaskDto::id, t -> t, (a, b) -> a));
        Map<Long, ShopItemDto> shopMap = shopItems.stream()
            .collect(java.util.stream.Collectors.toMap(ShopItemDto::id, s -> s, (a, b) -> a));
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
            log.warn("Failed to parse child group order payload={}", rawGroupOrder, ex);
            return List.of();
        }
    }

    private void syncFamilyRules(String familyId, Map<String, Object> payload, boolean adminSession) {
        if (!adminSession || !payload.containsKey("rules")) {
            return;
        }

        familyRepository.updateRules(familyId, asNullableString(payload.get("rules")));
    }

    private Integer resolveSelectedChildId(String familyId, Integer explicitChildId,
                                           Map<String, Object> payload,
                                           List<ChildEntity> children,
                                           boolean adminSession) {
        if (!adminSession) {
            return explicitChildId != null ? explicitChildId : children.getFirst().getId();
        }

        if (explicitChildId != null) {
            return explicitChildId;
        }

        Integer inferredChildId = inferSingleChildId(payload);
        if (inferredChildId != null) {
            return inferredChildId;
        }

        Integer persistedChildId = familyRepository.getLastSelectedChildId(familyId).orElse(null);
        if (persistedChildId != null
            && children.stream().anyMatch(child -> Objects.equals(child.getId(), persistedChildId))) {
            return persistedChildId;
        }

        return children.getFirst().getId();
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

    private Optional<ChildEntity> findFamilyChild(int familyDbId, int childId) {
        return childRepository.findByIdOptional(childId)
            .filter(child -> Objects.equals(child.getFamilyDbId(), familyDbId));
    }

    private Integer inferSingleChildId(Map<String, Object> payload) {
        Set<Integer> childIds = new LinkedHashSet<>();
        collectChildIds(childIds, payload.get("tasks"));
        collectChildIds(childIds, payload.get("shop"));
        collectChildIds(childIds, payload.get("history"));
        return childIds.size() == 1 ? childIds.iterator().next() : null;
    }

    private void collectChildIds(Set<Integer> childIds, Object rawEntries) {
        for (Map<String, Object> entry : asMapList(rawEntries)) {
            Integer entryChildId = asInteger(entry.get("childId"));
            if (entryChildId != null) {
                childIds.add(entryChildId);
            }
        }
    }

    private void syncBalances(int familyDbId, Integer selectedChildId,
                              Map<String, Object> payload,
                              List<ChildEntity> children) {
        if (selectedChildId != null) {
            Integer currentBalance = asInteger(payload.get("balance"));
            if (currentBalance != null) {
                childRepository.updateBalance(selectedChildId, currentBalance);
            }
        }

        Set<Integer> allowedChildIds = children.stream()
            .map(ChildEntity::getId)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        for (Map<String, Object> childPayload : asMapList(payload.get("children"))) {
            Integer childId = asInteger(childPayload.get("id"));
            Integer balance = asInteger(childPayload.get("balance"));
            if (childId == null
                || balance == null
                || !allowedChildIds.contains(childId)
                || Objects.equals(childId, selectedChildId)) {
                continue;
            }
            childRepository.updateBalance(childId, balance);
        }
    }

    private void syncTasks(int familyDbId, Integer selectedChildId, Map<String, Object> payload) {
        if (selectedChildId == null || !payload.containsKey("tasks")) {
            return;
        }

        familyDataRepository.markAllTasksDeleted(selectedChildId);
        for (Map<String, Object> task : asMapList(payload.get("tasks"))) {
            Long taskId = asLong(task.get("id"));
            String name = asString(task.get("name"));
            if (taskId == null || name == null || name.isBlank()) {
                continue;
            }

            familyDataRepository.upsertTask(new TaskUpsertCommand(
                familyDbId,
                selectedChildId,
                taskId,
                name,
                defaultInt(task.get("coins"), 0),
                firstNonBlank(asString(task.get("groupName")), asString(task.get("group"))),
                serializeFrequency(task.get("frequency")),
                asString(task.get("comment")),
                coalesceInt(task.get("moneyLimit"), task.get("money_limit")),
                defaultBoolean(coalesceFirst(task.get("isActive"), task.get("is_active")), true),
                defaultBoolean(task.get("isDeleted"), false)
            ));
        }
    }

    private void syncShopItems(int familyDbId, Integer selectedChildId, Map<String, Object> payload) {
        if (selectedChildId == null || !payload.containsKey("shop")) {
            return;
        }

        familyDataRepository.markAllShopItemsDeleted(selectedChildId);
        for (Map<String, Object> item : asMapList(payload.get("shop"))) {
            Long itemId = asLong(item.get("id"));
            String name = asString(item.get("name"));
            if (itemId == null || name == null || name.isBlank()) {
                continue;
            }

            familyDataRepository.upsertShopItem(new ShopItemUpsertCommand(
                familyDbId,
                selectedChildId,
                itemId,
                name,
                defaultInt(item.get("price"), 0),
                firstNonBlank(asString(item.get("groupName")), asString(item.get("group"))),
                serializeFrequency(item.get("frequency")),
                asString(item.get("comment")),
                coalesceInt(item.get("moneyLimit"), item.get("money_limit")),
                defaultBoolean(coalesceFirst(item.get("isActive"), item.get("is_active")), true),
                defaultBoolean(item.get("isDeleted"), false)
            ));
        }
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

    private List<Map<String, Object>> asMapList(Object rawValue) {
        if (!(rawValue instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                result.add(objectMapper.convertValue(
                    map,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }
                ));
            }
        }
        return result;
    }

    private String asNullableString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s.isBlank() ? null : s;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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
                log.debug("Failed to parse stored frequency JSON text node: {}", value, ex);
                return value;
            }
        }

        return objectMapper.convertValue(rawFrequency, Object.class);
    }

    private JsonNode serializeFrequency(Object rawFrequency) {
        if (rawFrequency == null) {
            return null;
        }
        if (rawFrequency instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        if (rawFrequency instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readTree(text);
            } catch (Exception ex) {
                log.warn("Failed to parse frequency payload as JSON string: {}", text, ex);
                return TextNode.valueOf(text);
            }
        }

        try {
            return objectMapper.valueToTree(rawFrequency);
        } catch (Exception ex) {
            log.warn("Failed to convert frequency payload to JSON: {}", rawFrequency, ex);
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer coalesceInt(Object primary, Object fallback) {
        Integer primaryValue = asInteger(primary);
        return primaryValue != null ? primaryValue : asInteger(fallback);
    }

    private Object coalesceFirst(Object primary, Object fallback) {
        return primary != null ? primary : fallback;
    }

    private int defaultInt(Object value, int defaultValue) {
        Integer parsed = asInteger(value);
        return parsed != null ? parsed : defaultValue;
    }

    private boolean defaultBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private List<HistoryEntryEntity> queryHistory(int familyDbId, Integer childId, Instant from, Instant to) {
        if (childId != null) {
            return historyRepository.list(
                "familyId = ?1 AND childId = ?2 AND createdAt >= ?3 AND createdAt < ?4",
                familyDbId, childId, from, to);
        }

        return historyRepository.list("familyId = ?1 AND createdAt >= ?2 AND createdAt < ?3",
            familyDbId, from, to);
    }

    private List<TaskEntity> queryTasks(int familyDbId, Integer childId) {
        if (childId != null) {
            return taskRepository.list("familyId = ?1 AND childId = ?2 AND deleted = false", familyDbId, childId);
        }
        return taskRepository.list("familyId = ?1 AND deleted = false", familyDbId);
    }

    private List<ShopItemEntity> queryShopItems(int familyDbId, Integer childId) {
        if (childId != null) {
            return shopItemRepository.list("familyId = ?1 AND childId = ?2 AND deleted = false", familyDbId, childId);
        }
        return shopItemRepository.list("familyId = ?1 AND deleted = false", familyDbId);
    }

    private Duration resolveTimeframeDuration(String timeframe) {
        if ("week".equalsIgnoreCase(timeframe)) {
            return Duration.ofDays(7);
        }
        if ("year".equalsIgnoreCase(timeframe)) {
            return Duration.ofDays(365);
        }
        return Duration.ofDays(30);
    }

    // EXPLAIN: Build top task stats from SQL-aggregated rows: [relatedId, sumAmount, count].
    private List<AnalyticsResponse.AnalyticsStatItem> buildTopTaskStatsAggregated(
            List<Object[]> aggregatedRows, List<TaskEntity> tasks) {
        Map<Long, String> namesByTaskId = tasks.stream()
            .collect(java.util.stream.Collectors.toMap(TaskEntity::getTaskId, TaskEntity::getName,
                (left, right) -> left));

        return aggregatedRows.stream()
            .map(row -> {
                Long relatedId = row[0] instanceof Number n ? n.longValue() : null;
                int coins = ((Number) row[1]).intValue();
                int count = ((Number) row[2]).intValue();
                String name = relatedId != null ? namesByTaskId.get(relatedId) : null;
                if (name == null || name.isBlank()) {
                    name = BackendMessages.message("analytics.taskFallback");
                }
                return new AnalyticsResponse.AnalyticsStatItem(name, coins, count);
            })
            .toList();
    }

    // EXPLAIN: Build top item stats from SQL-aggregated rows: [relatedId, sumAmount, count].
    private List<AnalyticsResponse.AnalyticsStatItem> buildTopItemStatsAggregated(
            List<Object[]> aggregatedRows, List<ShopItemEntity> items) {
        Map<Long, String> namesByItemId = items.stream()
            .collect(java.util.stream.Collectors.toMap(ShopItemEntity::getItemId, ShopItemEntity::getName,
                (left, right) -> left));

        return aggregatedRows.stream()
            .map(row -> {
                Long relatedId = row[0] instanceof Number n ? n.longValue() : null;
                int coins = ((Number) row[1]).intValue();
                int count = ((Number) row[2]).intValue();
                String name = relatedId != null ? namesByItemId.get(relatedId) : null;
                if (name == null || name.isBlank()) {
                    name = BackendMessages.message("analytics.itemFallback");
                }
                return new AnalyticsResponse.AnalyticsStatItem(name, coins, count);
            })
            .toList();
    }

    // EXPLAIN: Build daily trend from SQL-aggregated rows: [date, type, sumAmount].
    private List<AnalyticsResponse.AnalyticsTrendPoint> buildTrendsAggregated(List<Object[]> dailyRows) {
        // EXPLAIN: Aggregate by date: each row is [LocalDate, type, sumAmount]
        var perDay = new LinkedHashMap<LocalDate, AnalyticsResponse.AnalyticsSummary>();
        for (var row : dailyRows) {
            LocalDate day = row[0] instanceof java.sql.Date d ? d.toLocalDate()
                : row[0] instanceof LocalDate ld ? ld : null;
            if (day == null) {
                continue;
            }
            String type = (String) row[1];
            int amount = ((Number) row[2]).intValue();
            var agg = perDay.computeIfAbsent(day, unused -> new AnalyticsResponse.AnalyticsSummary(0, 0, 0));
            if ("earn".equals(type)) {
                var earnedSummary = new AnalyticsResponse.AnalyticsSummary(amount, agg.totalSpent(), amount);
                perDay.put(day, earnedSummary);
            } else if ("spend".equals(type)) {
                int net = agg.totalEarned() - amount;
                perDay.put(day, new AnalyticsResponse.AnalyticsSummary(agg.totalEarned(), amount, net));
            }
        }

        return perDay.entrySet().stream()
            .map(entry -> new AnalyticsResponse.AnalyticsTrendPoint(
                entry.getKey().toString(),
                entry.getValue().totalEarned(),
                entry.getValue().totalSpent()))
            .toList();
    }

    private List<AnalyticsResponse.AnalyticsRecommendation> buildRecommendations(int familyDbId, Integer childId) {
        Instant now = timeProvider.now();
        Instant lastMonthStart = now.minus(Duration.ofDays(30));
        List<TaskEntity> tasks = queryTasks(familyDbId, childId);
        if (tasks.isEmpty()) {
            return List.of();
        }

        List<HistoryEntryEntity> monthlyHistory = queryHistory(familyDbId, childId, lastMonthStart, now)
            .stream()
            .filter(entry -> entry.getType() == HistoryEntryType.earn)
            .toList();

        Map<Long, Integer> completionCounts = new LinkedHashMap<>();
        for (HistoryEntryEntity entry : monthlyHistory) {
            if (entry.getRelatedId() == null) {
                continue;
            }
            int nextCount = completionCounts.getOrDefault(entry.getRelatedId(), 0) + 1;
            completionCounts.put(entry.getRelatedId(), nextCount);
        }

        return tasks.stream()
            .sorted(Comparator
                .comparingInt((TaskEntity task) -> completionCounts.getOrDefault(task.getTaskId(), 0))
                .thenComparing(TaskEntity::getCoins, Comparator.reverseOrder()))
            .limit(3)
            .map(task -> new AnalyticsResponse.AnalyticsRecommendation(
                task.getName(),
                task.getCoins(),
                completionCounts.getOrDefault(task.getTaskId(), 0) == 0
                    ? BackendMessages.message("analytics.recommendationStale")
                    : BackendMessages.message("analytics.recommendationRepeat")
            ))
            .toList();
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

    private int[] normalizeSummary(int[] summary) {
        if (summary == null || summary.length < 2) {
            return new int[]{0, 0};
        }
        return summary;
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
