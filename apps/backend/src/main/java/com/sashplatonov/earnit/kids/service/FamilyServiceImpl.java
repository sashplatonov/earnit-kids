package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
    private static final Set<String> VALID_GROUP_ORDER_SECTIONS = Set.of("tasks", "shop");
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
            Boolean adminFlag = adminSession ? Boolean.TRUE : null;
            return OperationResult.success(new FamilyDataResponse(
                0, rules, List.of(), List.of(), List.of(), List.of(), List.of(),
                adminFlag, List.of(), null, null, null, null));
        }

        List<ChildEntity> visibleChildren = resolveVisibleChildren(children, adminSession, childId);
        if (visibleChildren.isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
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

        List<TaskDto> tasks = loadTasks(activeChild.getId());

        List<ShopItemDto> shopItems = loadShopItems(activeChild.getId());

        List<HistoryEntryDto> history = familyDataRepository.getHistory(activeChild.getId(), 50, 0).stream()
            .map(historyEntry -> toHistoryDto(historyEntry, tasks, shopItems))
            .toList();

        List<RequestDto> requests = familyDataRepository.getRequests(familyDbId, 50, 0).stream()
            .filter(request -> adminSession || Objects.equals(request.getChildId(), activeChild.getId()))
            .map(request -> toRequestDto(
                request,
                Objects.equals(request.getChildId(), activeChild.getId()) ? tasks : List.of(),
                Objects.equals(request.getChildId(), activeChild.getId()) ? shopItems : List.of()
            ))
            .toList();

        var friendIds = familyDataRepository.getFriendChildIds(activeChild.getId());
        List<FriendDto> friends = childRepository.findByChildIds(friendIds).stream()
            .map(f -> new FriendDto(f.getId(), f.getName(), f.getBalance()))
            .toList();

        List<ChildDto> childDtos = visibleChildren.stream()
            .map(this::toChildDto)
            .toList();

        Boolean adminFlag = adminSession ? Boolean.TRUE : null;
        return OperationResult.success(
            new FamilyDataResponse(activeChild.getBalance(), rules, tasks, shopItems, history, requests,
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
            log.warn("deleteChild failed: child not found or family mismatch familyId={} childId={}", familyId, childId);
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
    public OperationResult<Void> updateChildTheme(String familyId, int childId, String theme) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }
        if (!VALID_THEMES.contains(theme)) {
            return failure("INVALID_THEME", "family.invalidTheme", Map.of("theme", String.valueOf(theme)));
        }
        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }
        childRepository.updateTheme(childId, theme);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<Void> updateChildGroupOrder(String familyId, int childId,
                                                       String section, List<String> groups,
                                                       boolean personalOrder) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        if (findFamilyChild(dbIdOpt.get(), childId).isEmpty()) {
            return failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        String normalizedSection = normalizeGroupOrderSection(section);
        if (normalizedSection == null) {
            return failure("INVALID_GROUP_ORDER_SECTION", "family.invalidGroupOrderSection",
                Map.of("section", String.valueOf(section)));
        }

        String serializedOrder;
        try {
            serializedOrder = serializeGroupOrder(groups);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize group order familyId={} childId={} section={}", familyId, childId, normalizedSection, ex);
            return failure("GROUP_ORDER_SAVE_FAILED", "family.groupOrderSaveFailed");
        }

        childRepository.updateGroupOrder(childId, normalizedSection, personalOrder, serializedOrder);
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
        List<HistoryEntryDto> items = rows.stream().map(historyEntry -> toHistoryDto(historyEntry, tasks, shopItems)).toList();
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
        List<RequestDto> items = rows.stream().map(request -> toRequestDto(request, List.of(), List.of())).toList();
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
    public OperationResult<Void> updatePreference(String familyId, String key, Object value) {
        if ("lastSelectedChildId".equals(key)) {
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

    private String normalizeGroupOrderSection(String section) {
        if (section == null) {
            return null;
        }

        String normalized = section.trim().toLowerCase();
        return VALID_GROUP_ORDER_SECTIONS.contains(normalized) ? normalized : null;
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
                defaultBoolean(coalesceFirst(task.get("isActive"), task.get("is_active")), true),
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
                defaultBoolean(coalesceFirst(item.get("isActive"), item.get("is_active")), true),
                defaultBoolean(item.get("isDeleted"), false)
            );
        }
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
                        ? BackendMessages.message("analytics.taskFallback")
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
                        ? BackendMessages.message("analytics.itemFallback")
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
                completionCounts.getOrDefault(task.getTaskId(), 0) == 0
                    ? BackendMessages.message("analytics.recommendationStale")
                    : BackendMessages.message("analytics.recommendationRepeat")
            ))
            .toList();
    }

    private static final class Aggregate {
        private int coins;
        private int count;
        private int earned;
        private int spent;
    }

    private List<TaskDto> loadTasks(int childId) {
        return familyDataRepository.getTasks(childId).stream().map(this::toTaskDto).toList();
    }

    private List<ShopItemDto> loadShopItems(int childId) {
        return familyDataRepository.getShopItems(childId).stream().map(this::toShopItemDto).toList();
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

    private TaskDto toTaskDto(TaskEntity task) {
        return new TaskDto(task.getTaskId(), task.getName(), task.getCoins(), task.getGroupName(),
            parseFrequency(task.getFrequency()), task.getComment(), task.getMoneyLimit(), task.isActive(), task.getChildId());
    }

    private ShopItemDto toShopItemDto(ShopItemEntity shopItem) {
        return new ShopItemDto(shopItem.getItemId(), shopItem.getName(), shopItem.getPrice(), shopItem.getGroupName(),
            parseFrequency(shopItem.getFrequency()), shopItem.getComment(), shopItem.getMoneyLimit(), shopItem.isActive(), shopItem.getChildId());
    }

    private HistoryEntryDto toHistoryDto(HistoryEntryEntity entry, List<TaskDto> tasks, List<ShopItemDto> shopItems) {
        HistoryDetails details = enrichHistoryDetails(entry, tasks, shopItems);
        return new HistoryEntryDto(entry.getExternalId(), entry.getType(), entry.getAmount(),
            details.title(),
            details.description(), entry.getMoneyAmount(), entry.getRelatedId(), details.taskId(),
            details.taskName(), details.itemId(), details.itemName(), details.groupName(), details.comment(),
            entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null,
            entry.getChildId());
    }

    private HistoryDetails enrichHistoryDetails(HistoryEntryEntity entry, List<TaskDto> tasks, List<ShopItemDto> shopItems) {
        if (entry.getRelatedId() == null) {
            return new HistoryDetails(entry.getDescription(), entry.getDescription(), null, null, null, null,
                entry.getGroupName(), entry.getComment());
        }

        if ("earn".equals(entry.getType())) {
            TaskDto task = findTaskDto(entry.getFamilyId(), entry.getChildId(), entry.getRelatedId(), tasks);
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

        if ("spend".equals(entry.getType())) {
            ShopItemDto shopItem = findShopItemDto(entry.getFamilyId(), entry.getChildId(), entry.getRelatedId(), shopItems);
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
        return "shop_purchase".equals(request.getRequestType()) || request.getItemId() != null;
    }

    private RequestDto toRequestDto(PurchaseRequestEntity request, List<TaskDto> tasks, List<ShopItemDto> shopItems) {
        RequestDetails details = enrichRequestDetails(request, tasks, shopItems);
        return new RequestDto(request.getId(), request.getTaskId(), details.taskName(),
            request.getItemId(), details.itemName(), details.title(), details.description(),
            details.groupName(), details.comment(), request.getCoins(), request.getStatus(), request.getRequestType(),
            request.getMoneyAmount(), request.getCreatedAt() != null ? request.getCreatedAt().toString() : null,
            request.getChildId(), details.taskGroup(), details.itemGroup(), details.taskComment(), details.itemComment());
    }

    private RequestDetails enrichRequestDetails(PurchaseRequestEntity request, List<TaskDto> tasks, List<ShopItemDto> shopItems) {
        boolean purchase = isPurchaseRequest(request) || request.getItemId() != null;
        Long itemId = request.getItemId() != null ? request.getItemId() : request.getTaskId();
        ShopItemDto shopItem = purchase && itemId != null
            ? findShopItemDto(request.getFamilyId(), request.getChildId(), itemId, shopItems)
            : null;
        TaskDto task = !purchase && request.getTaskId() != null
            ? findTaskDto(request.getFamilyId(), request.getChildId(), request.getTaskId(), tasks)
            : null;

        String taskName = firstNonBlank(request.getTaskName(), task != null ? task.name() : null);
        String itemName = purchase ? firstNonBlank(shopItem != null ? shopItem.name() : null, request.getTaskName()) : null;
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

    private TaskDto findTaskDto(int familyDbId, int childId, Long taskId, List<TaskDto> tasks) {
        if (taskId == null) {
            return null;
        }
        List<TaskDto> availableTasks = tasks.isEmpty() ? loadTasks(childId) : tasks;
        TaskDto fromLoaded = availableTasks.stream()
            .filter(candidate -> Objects.equals(candidate.id(), taskId))
            .findFirst()
            .orElse(null);
        if (fromLoaded != null) {
            return fromLoaded;
        }
        return taskRepository.find(
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 ORDER BY id DESC",
            familyDbId,
            childId,
            taskId
        ).firstResultOptional().map(this::toTaskDto).orElse(null);
    }

    private ShopItemDto findShopItemDto(int familyDbId, int childId, Long itemId, List<ShopItemDto> shopItems) {
        if (itemId == null) {
            return null;
        }
        List<ShopItemDto> availableShopItems = shopItems.isEmpty() ? loadShopItems(childId) : shopItems;
        ShopItemDto fromLoaded = availableShopItems.stream()
            .filter(candidate -> Objects.equals(candidate.id(), itemId))
            .findFirst()
            .orElse(null);
        if (fromLoaded != null) {
            return fromLoaded;
        }
        return shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 ORDER BY id DESC",
            familyDbId,
            childId,
            itemId
        ).firstResultOptional().map(this::toShopItemDto).orElse(null);
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
