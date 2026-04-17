package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
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
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyDataRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private static final Set<String> VALID_THEMES = Set.of("mint", "ocean", "sun", "coral", "cosmos");
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
            return OperationResult.failure("Семья не найдена");
        }
        int familyDbId = dbIdOpt.get();
        Integer persistedChildId = familyRepository.getLastSelectedChildId(familyId).orElse(null);

        List<ChildEntity> children = childRepository.getChildren(familyDbId);
        if (children.isEmpty()) {
            Boolean adminFlag = adminSession ? Boolean.TRUE : null;
            return OperationResult.success(new FamilyDataResponse(
                0, List.of(), List.of(), List.of(), List.of(), List.of(),
                adminFlag, List.of(), null, null, null, null));
        }

        List<ChildEntity> visibleChildren = resolveVisibleChildren(children, adminSession, childId);
        if (visibleChildren.isEmpty()) {
            return OperationResult.failure("Ребенок не найден");
        }

        Integer preferredChildId = adminSession
            ? (childId != null ? childId : persistedChildId)
            : visibleChildren.getFirst().getId();
        ChildEntity activeChild = preferredChildId != null
            ? visibleChildren.stream()
                .filter(c -> Objects.equals(c.getId(), preferredChildId))
                .findFirst()
                .orElse(visibleChildren.getFirst())
            : visibleChildren.getFirst();
        Integer resolvedLastSelectedChildId = adminSession
            ? children.stream()
                .map(ChildEntity::getId)
                .filter(id -> Objects.equals(id, persistedChildId))
                .findFirst()
                .orElse(activeChild.getId())
            : activeChild.getId();

        List<TaskDto> tasks = familyDataRepository.getTasks(activeChild.getId()).stream()
            .map(t -> new TaskDto(t.getTaskId(), t.getName(), t.getCoins(), t.getGroupName(),
                parseFrequency(t.getFrequency()), t.getComment(), t.getMoneyLimit(), t.getChildId()))
            .toList();

        List<ShopItemDto> shopItems = familyDataRepository.getShopItems(activeChild.getId()).stream()
            .map(s -> new ShopItemDto(s.getItemId(), s.getName(), s.getPrice(), s.getGroupName(),
                parseFrequency(s.getFrequency()), s.getComment(), s.getMoneyLimit(), s.getChildId()))
            .toList();

        List<HistoryEntryDto> history = familyDataRepository.getHistory(activeChild.getId(), 50, 0).stream()
            .map(historyEntry -> toHistoryDto(historyEntry, tasks, shopItems))
            .toList();

        List<RequestDto> requests = familyDataRepository.getRequests(familyDbId, 50, 0).stream()
            .filter(request -> adminSession || Objects.equals(request.getChildId(), activeChild.getId()))
            .map(request -> toRequestDto(request))
            .toList();

        var friendIds = familyDataRepository.getFriendChildIds(activeChild.getId());
        List<FriendDto> friends = childRepository.findByChildIds(friendIds).stream()
            .map(f -> new FriendDto(f.getId(), f.getName(), f.getBalance()))
            .toList();

        List<ChildDto> childDtos = visibleChildren.stream()
            .map(c -> new ChildDto(c.getId(), c.getName(), c.getBalance(),
                c.getMonthlyLimit(), c.getDailyCoinLimit(), c.getTheme()))
            .toList();

        Boolean adminFlag = adminSession ? Boolean.TRUE : null;
        return OperationResult.success(
            new FamilyDataResponse(activeChild.getBalance(), tasks, shopItems, history, requests,
                friends, adminFlag, childDtos, resolvedLastSelectedChildId, activeChild.getName(),
                activeChild.getMonthlyLimit(), activeChild.getDailyCoinLimit()));
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> saveFamilyData(String familyId, Integer childId,
                                                              Map<String, Object> payload,
                                                              boolean adminSession) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }

        int familyDbId = dbIdOpt.get();
        List<ChildEntity> children = childRepository.getChildren(familyDbId);
        if (children.isEmpty()) {
            return loadFamilyData(familyId, childId, adminSession);
        }

        List<ChildEntity> accessibleChildren = resolveVisibleChildren(children, adminSession, childId);
        if (accessibleChildren.isEmpty()) {
            return OperationResult.failure("Ребенок не найден");
        }

        Integer selectedChildId = resolveSelectedChildId(familyId, childId, payload, accessibleChildren, adminSession);
        if (selectedChildId != null
            && accessibleChildren.stream().noneMatch(child -> Objects.equals(child.getId(), selectedChildId))) {
            return OperationResult.failure("Ребенок не найден");
        }

        syncBalances(familyDbId, selectedChildId, payload, accessibleChildren);
        syncTasks(familyDbId, selectedChildId, payload);
        syncShopItems(familyDbId, selectedChildId, payload);
        syncHistory(familyDbId, selectedChildId, payload, accessibleChildren);
        syncRequests(familyDbId, selectedChildId, payload, accessibleChildren);
        familyRepository.updateLastActivity(familyId);

        return loadFamilyData(familyId, selectedChildId, adminSession);
    }

    @Override
    public OperationResult<ChildInfo> createChild(String familyId, String childName) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            log.warn("createChild failed: family not found familyId={}", familyId);
            return OperationResult.failure("Семья не найдена");
        }
        int familyDbId = dbIdOpt.get();

        if (childName == null || childName.isBlank()) {
            return OperationResult.failure("Имя ребенка обязательно");
        }
        if (childName.length() > 50) {
            return OperationResult.failure("Имя слишком длинное");
        }
        if (childRepository.isNicknameTaken(familyDbId, childName, null)) {
            return OperationResult.failure("Это имя уже занято");
        }

        Optional<ChildEntity> childOpt = childRepository.createChild(familyDbId, childName);
        if (childOpt.isEmpty()) {
            log.error("createChild failed: repository returned empty familyId={}", familyId);
            return OperationResult.failure("Ошибка создания");
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
            return OperationResult.failure("Семья не найдена");
        }

        var childOpt = childRepository.findByIdOptional(childId);
        if (childOpt.isEmpty() || !Objects.equals(childOpt.get().getFamilyDbId(), dbIdOpt.get())) {
            log.warn("deleteChild failed: child not found or family mismatch familyId={} childId={}", familyId, childId);
            return OperationResult.failure("Ребенок не найден");
        }

        childRepository.deleteChild(childId);
        log.info("Child deleted childId={} familyId={}", childId, familyId);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> updateNickname(String familyId, int childId, String newName) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }

        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return OperationResult.failure("Ребенок не найден");
        }

        if (newName == null || newName.isBlank()) {
            return OperationResult.failure("Имя обязательно");
        }
        if (childRepository.isNicknameTaken(dbIdOpt.get(), newName, childId)) {
            return OperationResult.failure("Это имя уже занято");
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
            return OperationResult.failure("Семья не найдена");
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return OperationResult.failure("Ребенок не найден");
        }
        childRepository.updateSettings(childId, name, dailyCoinLimit, monthlyLimit);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> updateChildTheme(String familyId, int childId, String theme) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }
        if (!VALID_THEMES.contains(theme)) {
            return OperationResult.failure("Недопустимая тема: " + theme);
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return OperationResult.failure("Ребенок не найден");
        }
        childRepository.updateTheme(childId, theme);
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
            return OperationResult.failure("Cannot add yourself");
        }

        if (familyRepository.getDbId(familyId).isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }

        if (childRepository.findByIdOptional(friendChildId).isEmpty()) {
            return OperationResult.failure("User not found");
        }

        boolean saved = familyDataRepository.addFriend(childId, friendChildId);
        if (!saved) {
            return OperationResult.failure("Already friends or failed to add");
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
            return OperationResult.failure("Семья не найдена");
        }

        int familyDbId = familyDbIdOpt.get();
        Duration periodDuration = resolveTimeframeDuration(timeframe);
        Instant now = timeProvider.now();
        Instant periodStart = now.minus(periodDuration);
        Instant previousStart = periodStart.minus(periodDuration);

        List<HistoryEntryEntity> currentPeriodHistory = queryHistory(familyDbId, childId, periodStart, now);
        List<HistoryEntryEntity> previousPeriodHistory = queryHistory(familyDbId, childId, previousStart, periodStart);
        List<TaskEntity> tasks = queryTasks(familyDbId, childId);
        List<ShopItemEntity> items = queryShopItems(familyDbId, childId);

        AnalyticsResponse.AnalyticsSummary summary = summarize(currentPeriodHistory);
        AnalyticsResponse.AnalyticsSummary comparison = summarize(previousPeriodHistory);
        List<AnalyticsResponse.AnalyticsStatItem> topTasks = buildTopTaskStats(currentPeriodHistory, tasks);
        List<AnalyticsResponse.AnalyticsStatItem> topItems = buildTopItemStats(currentPeriodHistory, items);
        List<AnalyticsResponse.AnalyticsTrendPoint> trends = buildTrends(currentPeriodHistory);
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
            return OperationResult.failure("Семья не найдена");
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return OperationResult.failure("Ребенок не найден");
        }

        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int offset = (page - 1) * effectiveLimit;
        List<HistoryEntryEntity> rows = familyDataRepository.getHistory(childId, effectiveLimit, offset);
        int total = familyDataRepository.getHistoryCount(childId);
        List<TaskDto> tasks = familyDataRepository.getTasks(childId).stream()
            .map(t -> new TaskDto(t.getTaskId(), t.getName(), t.getCoins(), t.getGroupName(),
                parseFrequency(t.getFrequency()), t.getComment(), t.getMoneyLimit(), t.getChildId()))
            .toList();
        List<ShopItemDto> shopItems = familyDataRepository.getShopItems(childId).stream()
            .map(s -> new ShopItemDto(s.getItemId(), s.getName(), s.getPrice(), s.getGroupName(),
                parseFrequency(s.getFrequency()), s.getComment(), s.getMoneyLimit(), s.getChildId()))
            .toList();
        List<HistoryEntryDto> items = rows.stream().map(historyEntry -> toHistoryDto(historyEntry, tasks, shopItems)).toList();
        return OperationResult.success(new PaginatedHistory(items, total, page, effectiveLimit));
    }

    @Override
    public OperationResult<PaginatedRequests> getRequests(String familyId, int page, int limit) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }
        int familyDbId = dbIdOpt.get();
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int offset = (page - 1) * effectiveLimit;
        List<PurchaseRequestEntity> rows = familyDataRepository.getRequests(familyDbId, effectiveLimit, offset);
        int total = familyDataRepository.getRequestsCount(familyDbId);
        List<RequestDto> items = rows.stream().map(request -> toRequestDto(request)).toList();
        return OperationResult.success(new PaginatedRequests(items, total, page, effectiveLimit));
    }

    @Override
    public OperationResult<String> getChildLoginLink(String familyId, int childId) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }

        var childOpt = findFamilyChild(dbIdOpt.get(), childId);
        if (childOpt.isEmpty()) {
            return OperationResult.failure("Ребенок не найден");
        }
        return OperationResult.success(childOpt.get().getToken());
    }

    @Override
    public OperationResult<String> regenerateChildToken(String familyId, int childId) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return OperationResult.failure("Семья не найдена");
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return OperationResult.failure("Ребенок не найден");
        }

        Optional<String> newToken = childRepository.regenerateToken(childId);
        if (newToken.isEmpty()) {
            return OperationResult.failure("Ошибка генерации токена");
        }
        return OperationResult.success(newToken.get());
    }

    @Override
    public OperationResult<Void> updatePreference(String familyId, String key, Object value) {
        if ("lastSelectedChildId".equals(key)) {
            Optional<Integer> familyDbIdOpt = familyRepository.getDbId(familyId);
            if (familyDbIdOpt.isEmpty()) {
                return OperationResult.failure("Семья не найдена");
            }

            Integer childId = parseChildIdPreference(value);
            if (value != null && childId == null) {
                return OperationResult.failure("Некорректный идентификатор ребенка");
            }
            if (childId != null) {
                Optional<ChildEntity> childOpt = childRepository.findByIdOptional(childId);
                if (childOpt.isEmpty() || !Objects.equals(childOpt.get().getFamilyDbId(), familyDbIdOpt.get())) {
                    return OperationResult.failure("Ребенок не найден");
                }
            }

            familyRepository.updateLastSelectedChild(familyId, childId);
            return OperationResult.success(null);
        }
        return OperationResult.failure("Неизвестная настройка: " + key);
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
        if (persistedChildId != null && children.stream().anyMatch(child -> Objects.equals(child.getId(), persistedChildId))) {
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

            familyDataRepository.upsertTask(
                familyDbId,
                selectedChildId,
                taskId,
                name,
                defaultInt(task.get("coins"), 0),
                firstNonBlank(asString(task.get("groupName")), asString(task.get("group"))),
                serializeFrequency(task.get("frequency")),
                asString(task.get("comment")),
                coalesceInt(task.get("moneyLimit"), task.get("money_limit")),
                defaultBoolean(task.get("isDeleted"), false)
            );
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

            familyDataRepository.upsertShopItem(
                familyDbId,
                selectedChildId,
                itemId,
                name,
                defaultInt(item.get("price"), 0),
                firstNonBlank(asString(item.get("groupName")), asString(item.get("group"))),
                serializeFrequency(item.get("frequency")),
                asString(item.get("comment")),
                coalesceInt(item.get("moneyLimit"), item.get("money_limit")),
                defaultBoolean(item.get("isDeleted"), false)
            );
        }
    }

    private void syncHistory(int familyDbId, Integer selectedChildId, Map<String, Object> payload,
                              List<ChildEntity> accessibleChildren) {
        if (selectedChildId == null || !payload.containsKey("history")) {
            return;
        }

        Map<Long, Instant> existingCreatedAtByExternalId = mapHistoryCreatedAtByExternalId(
            familyDataRepository.getAllHistoryForFamily(familyDbId));

        Set<Integer> allowedChildIds = accessibleChildren.stream()
            .map(ChildEntity::getId)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        List<HistoryEntryEntity> selectedChildEntries = new ArrayList<>();
        List<HistoryEntryEntity> otherChildEntries = new ArrayList<>();

        for (Map<String, Object> entry : asMapList(payload.get("history"))) {
            Integer entryChildId = asInteger(entry.get("childId"));
            int targetChildId = (entryChildId != null && allowedChildIds.contains(entryChildId))
                ? entryChildId
                : selectedChildId;
            Long externalId = asLong(entry.get("id"));

            HistoryEntryEntity entity = HistoryEntryEntity.builder()
                .familyId(familyDbId)
                .childId(targetChildId)
                .externalId(externalId)
                .type(firstNonBlank(asString(entry.get("type")), "unknown"))
                .amount(firstDefinedInt(entry.get("amount"), entry.get("coins"), 0))
                .description(asString(entry.get("description")))
                .moneyAmount(firstDefinedInt(entry.get("moneyAmount"), entry.get("money_amount"), 0))
                .relatedId(firstDefinedLong(entry.get("relatedId"), entry.get("taskId"), entry.get("itemId")))
                .groupName(firstNonBlank(asString(entry.get("groupName")), asString(entry.get("group"))))
                .comment(asString(entry.get("comment")))
                .createdAt(resolveCreatedAt(externalId, existingCreatedAtByExternalId,
                    entry.get("createdAt"), entry.get("created_at"), entry.get("date"), entry.get("timestamp")))
                .build();

            if (targetChildId == selectedChildId) {
                selectedChildEntries.add(entity);
            } else {
                otherChildEntries.add(entity);
            }
        }

        familyDataRepository.replaceHistory(familyDbId, selectedChildId, selectedChildEntries);
        for (HistoryEntryEntity entry : otherChildEntries) {
            familyDataRepository.upsertHistoryEntry(entry);
        }
    }

    private void syncRequests(int familyDbId, Integer selectedChildId, Map<String, Object> payload,
                              List<ChildEntity> children) {
        if (!payload.containsKey("requests")) {
            return;
        }

        Map<Long, Instant> existingCreatedAtByExternalId = mapRequestCreatedAtByExternalId(
            familyDataRepository.getAllRequestsForFamily(familyDbId));

        Set<Integer> allowedChildIds = children.stream()
            .map(ChildEntity::getId)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        int fallbackChildId = selectedChildId != null ? selectedChildId : children.getFirst().getId();

        List<PurchaseRequestEntity> entries = new ArrayList<>();
        for (Map<String, Object> request : asMapList(payload.get("requests"))) {
            Integer requestChildId = asInteger(request.get("childId"));
            int targetChildId = requestChildId != null && allowedChildIds.contains(requestChildId)
                ? requestChildId
                : fallbackChildId;
            Long externalId = asLong(request.get("id"));

            entries.add(PurchaseRequestEntity.builder()
                .familyId(familyDbId)
                .childId(targetChildId)
                .externalId(externalId)
                .taskId(asLong(request.get("taskId")))
                .taskName(asString(request.get("taskName")))
                .itemId(asLong(request.get("itemId")))
                .coins(defaultInt(request.get("coins"), 0))
                .status(firstNonBlank(asString(request.get("status")), "pending"))
                .requestType(firstNonBlank(asString(request.get("requestType")), "earn"))
                .moneyAmount(firstDefinedInt(request.get("moneyAmount"), request.get("money_amount"), 0))
                .createdAt(resolveCreatedAt(externalId, existingCreatedAtByExternalId,
                    request.get("createdAt"), request.get("created_at"), request.get("date"), request.get("timestamp")))
                .build());
        }
        familyDataRepository.replaceRequests(familyDbId, entries);
    }

    private List<Map<String, Object>> asMapList(Object rawValue) {
        if (!(rawValue instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                result.add(objectMapper.convertValue(map, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { }));
            }
        }
        return result;
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

    private Instant parseInstant(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (candidate instanceof Instant instant) {
                return instant;
            }
            if (candidate instanceof Number number) {
                Instant parsed = parseEpochTimestamp(number.longValue());
                if (parsed != null) {
                    return parsed;
                }
            }
            if (candidate instanceof String value && !value.isBlank()) {
                try {
                    return Instant.parse(value);
                } catch (Exception ignored) {
                }
                Instant parsed = parseEpochTimestamp(asLong(value));
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private Instant resolveCreatedAt(Long externalId, Map<Long, Instant> existingCreatedAtByExternalId,
                                     Object... candidates) {
        Instant parsed = parseInstant(candidates);
        if (parsed != null) {
            return parsed;
        }

        if (externalId != null) {
            Instant existing = existingCreatedAtByExternalId.get(externalId);
            if (existing != null) {
                return existing;
            }

            Instant derived = parseEpochTimestamp(externalId);
            if (derived != null) {
                return derived;
            }
        }

        return timeProvider.now();
    }

    private Instant parseEpochTimestamp(Long value) {
        if (value == null) {
            return null;
        }
        if (value >= 946684800000L && value <= 4102444800000L) {
            return Instant.ofEpochMilli(value);
        }
        if (value >= 946684800L && value <= 4102444800L) {
            return Instant.ofEpochSecond(value);
        }
        return null;
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

    private int defaultInt(Object value, int defaultValue) {
        Integer parsed = asInteger(value);
        return parsed != null ? parsed : defaultValue;
    }

    private int firstDefinedInt(Object primary, Object fallback, int defaultValue) {
        Integer value = coalesceInt(primary, fallback);
        return value != null ? value : defaultValue;
    }

    private Long firstDefinedLong(Object... values) {
        for (Object value : values) {
            Long parsed = asLong(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
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

    private Map<Long, Instant> mapHistoryCreatedAtByExternalId(List<HistoryEntryEntity> entries) {
        Map<Long, Instant> createdAtByExternalId = new LinkedHashMap<>();
        if (entries == null) {
            return createdAtByExternalId;
        }
        for (HistoryEntryEntity entry : entries) {
            if (entry.getExternalId() != null && entry.getCreatedAt() != null) {
                createdAtByExternalId.put(entry.getExternalId(), entry.getCreatedAt());
            }
        }
        return createdAtByExternalId;
    }

    private Map<Long, Instant> mapRequestCreatedAtByExternalId(List<PurchaseRequestEntity> entries) {
        Map<Long, Instant> createdAtByExternalId = new LinkedHashMap<>();
        if (entries == null) {
            return createdAtByExternalId;
        }
        for (PurchaseRequestEntity entry : entries) {
            if (entry.getExternalId() != null && entry.getCreatedAt() != null) {
                createdAtByExternalId.put(entry.getExternalId(), entry.getCreatedAt());
            }
        }
        return createdAtByExternalId;
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

    private AnalyticsResponse.AnalyticsSummary summarize(List<HistoryEntryEntity> historyEntries) {
        int totalEarned = historyEntries.stream()
            .filter(entry -> "earn".equals(entry.getType()))
            .mapToInt(HistoryEntryEntity::getAmount)
            .sum();
        int totalSpent = historyEntries.stream()
            .filter(entry -> "spend".equals(entry.getType()))
            .mapToInt(HistoryEntryEntity::getAmount)
            .sum();

        return new AnalyticsResponse.AnalyticsSummary(totalEarned, totalSpent, totalEarned - totalSpent);
    }

    private List<AnalyticsResponse.AnalyticsStatItem> buildTopTaskStats(List<HistoryEntryEntity> historyEntries,
                                                                        List<TaskEntity> tasks) {
        Map<Long, String> namesByTaskId = tasks.stream()
            .collect(java.util.stream.Collectors.toMap(TaskEntity::getTaskId, TaskEntity::getName,
                (left, right) -> left));

        Map<String, Aggregate> byName = new LinkedHashMap<>();
        historyEntries.stream()
            .filter(entry -> "earn".equals(entry.getType()))
            .forEach(entry -> {
                String name = null;
                if (entry.getRelatedId() != null) {
                    name = namesByTaskId.get(entry.getRelatedId());
                }
                if (name == null || name.isBlank()) {
                    name = entry.getDescription() == null || entry.getDescription().isBlank()
                        ? "Задание"
                        : entry.getDescription();
                }

                Aggregate aggregate = byName.computeIfAbsent(name, unused -> new Aggregate());
                aggregate.coins += entry.getAmount();
                aggregate.count += 1;
            });

        return toTopStats(byName);
    }

    private List<AnalyticsResponse.AnalyticsStatItem> buildTopItemStats(List<HistoryEntryEntity> historyEntries,
                                                                        List<ShopItemEntity> items) {
        Map<Long, String> namesByItemId = items.stream()
            .collect(java.util.stream.Collectors.toMap(ShopItemEntity::getItemId, ShopItemEntity::getName,
                (left, right) -> left));

        Map<String, Aggregate> byName = new LinkedHashMap<>();
        historyEntries.stream()
            .filter(entry -> "spend".equals(entry.getType()))
            .forEach(entry -> {
                String name = null;
                if (entry.getRelatedId() != null) {
                    name = namesByItemId.get(entry.getRelatedId());
                }
                if (name == null || name.isBlank()) {
                    name = entry.getDescription() == null || entry.getDescription().isBlank()
                        ? "Товар"
                        : entry.getDescription();
                }

                Aggregate aggregate = byName.computeIfAbsent(name, unused -> new Aggregate());
                aggregate.coins += entry.getAmount();
                aggregate.count += 1;
            });

        return toTopStats(byName);
    }

    private List<AnalyticsResponse.AnalyticsStatItem> toTopStats(Map<String, Aggregate> byName) {
        return byName.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, Aggregate> entry) -> entry.getValue().coins)
                .reversed())
            .map(entry -> new AnalyticsResponse.AnalyticsStatItem(
                entry.getKey(),
                entry.getValue().coins,
                entry.getValue().count
            ))
            .toList();
    }

    private List<AnalyticsResponse.AnalyticsTrendPoint> buildTrends(List<HistoryEntryEntity> historyEntries) {
        Map<LocalDate, Aggregate> perDay = new LinkedHashMap<>();
        historyEntries.stream()
            .sorted(Comparator.comparing(HistoryEntryEntity::getCreatedAt))
            .forEach(entry -> {
                if (entry.getCreatedAt() == null) {
                    return;
                }
                LocalDate day = entry.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
                Aggregate aggregate = perDay.computeIfAbsent(day, unused -> new Aggregate());
                if ("earn".equals(entry.getType())) {
                    aggregate.earned += entry.getAmount();
                } else if ("spend".equals(entry.getType())) {
                    aggregate.spent += entry.getAmount();
                }
            });

        List<AnalyticsResponse.AnalyticsTrendPoint> trends = new ArrayList<>();
        perDay.forEach((day, aggregate) -> {
            trends.add(new AnalyticsResponse.AnalyticsTrendPoint(
                day.toString(),
                aggregate.earned,
                aggregate.spent
            ));
        });
        return trends;
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
            .filter(entry -> "earn".equals(entry.getType()))
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
                completionCounts.getOrDefault(task.getTaskId(), 0) == 0 ? "Давно не выполнялось" : "Стоит повторить"
            ))
            .toList();
    }

    private static final class Aggregate {
        private int coins;
        private int count;
        private int earned;
        private int spent;
    }

    private HistoryEntryDto toHistoryDto(HistoryEntryEntity entry, List<TaskDto> tasks, List<ShopItemDto> shopItems) {
        HistoryDetails details = enrichHistoryDetails(entry, tasks, shopItems);
        return new HistoryEntryDto(entry.getExternalId(), entry.getType(), entry.getAmount(),
            details.description(), entry.getMoneyAmount(), entry.getRelatedId(), details.taskId(),
            details.itemId(), details.groupName(), details.comment(),
            entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null,
            entry.getChildId());
    }

    private HistoryDetails enrichHistoryDetails(HistoryEntryEntity entry, List<TaskDto> tasks, List<ShopItemDto> shopItems) {
        if (entry.getRelatedId() == null) {
            return new HistoryDetails(entry.getDescription(), null, null, entry.getGroupName(), entry.getComment());
        }

        if ("earn".equals(entry.getType())) {
            TaskDto task = tasks.stream()
                .filter(candidate -> candidate.id() == entry.getRelatedId())
                .findFirst()
                .orElse(null);
            if (task != null) {
                return new HistoryDetails(
                    firstNonBlank(entry.getDescription(), task.name()),
                    task.id(),
                    null,
                    firstNonBlank(entry.getGroupName(), task.groupName()),
                    firstNonBlank(entry.getComment(), task.comment())
                );
            }
        }

        if ("spend".equals(entry.getType())) {
            ShopItemDto shopItem = shopItems.stream()
                .filter(candidate -> candidate.id() == entry.getRelatedId())
                .findFirst()
                .orElse(null);
            if (shopItem != null) {
                return new HistoryDetails(
                    firstNonBlank(entry.getDescription(), shopItem.name()),
                    null,
                    shopItem.id(),
                    firstNonBlank(entry.getGroupName(), shopItem.groupName()),
                    firstNonBlank(entry.getComment(), shopItem.comment())
                );
            }
        }

        return new HistoryDetails(entry.getDescription(), null, null, entry.getGroupName(), entry.getComment());
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private RequestDto toRequestDto(PurchaseRequestEntity r) {
        return new RequestDto(r.getId(), r.getTaskId(), r.getTaskName(),
            r.getItemId(), null, r.getCoins(), r.getStatus(), r.getRequestType(),
            r.getMoneyAmount(), r.getCreatedAt() != null ? r.getCreatedAt().toString() : null,
            r.getChildId(), null, null, null);
    }

    private record HistoryDetails(
        String description,
        Long taskId,
        Long itemId,
        String groupName,
        String comment
    ) { }
}
