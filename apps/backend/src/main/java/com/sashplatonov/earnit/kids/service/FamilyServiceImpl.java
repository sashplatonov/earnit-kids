package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.dto.request.ChildTheme;
import com.sashplatonov.earnit.kids.dto.request.FamilyPreferenceKey;
import com.sashplatonov.earnit.kids.dto.request.GroupOrderSection;
import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.FriendDto;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.FriendRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public final class FamilyServiceImpl implements FamilyService {
    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final FriendRepository friendRepository;
    private final ObjectMapper objectMapper;
    private final FamilyDashboardQueryService familyDashboardQueryService;
    private final FamilyHistoryQueryService familyHistoryQueryService;
    private final FamilyCommandService familyCommandService;
    private final AnalyticsService analyticsService;

    @Inject
    public FamilyServiceImpl(FamilyRepository familyRepository,
                             ChildRepository childRepository,
                             FriendRepository friendRepository,
                             ObjectMapper objectMapper,
                             FamilyDashboardQueryService familyDashboardQueryService,
                             FamilyHistoryQueryService familyHistoryQueryService,
                             FamilyCommandService familyCommandService,
                             AnalyticsService analyticsService) {
        this.familyRepository = familyRepository;
        this.childRepository = childRepository;
        this.friendRepository = friendRepository;
        this.objectMapper = objectMapper;
        this.familyDashboardQueryService = familyDashboardQueryService;
        this.familyHistoryQueryService = familyHistoryQueryService;
        this.familyCommandService = familyCommandService;
        this.analyticsService = analyticsService;
    }

    FamilyServiceImpl(FamilyRepository familyRepository,
                      ChildRepository childRepository,
                      TaskRepository taskRepository,
                      ShopItemRepository shopItemRepository,
                      HistoryRepository historyRepository,
                      PurchaseRequestRepository purchaseRequestRepository,
                      FriendRepository friendRepository,
                      com.sashplatonov.earnit.kids.util.TimeProvider timeProvider,
                      BackendKpiMetrics backendKpiMetrics) {
        ObjectMapper mapper = new ObjectMapper();
        AnalyticsService analyticsService = new AnalyticsServiceImpl(
            familyRepository,
            historyRepository,
            taskRepository,
            shopItemRepository,
            timeProvider,
            backendKpiMetrics
        );
        FamilyDashboardQueryService familyDashboardQueryService = new FamilyDashboardQueryServiceImpl(
            familyRepository,
            childRepository,
            historyRepository,
            purchaseRequestRepository,
            friendRepository,
            taskRepository,
            shopItemRepository,
            mapper,
            backendKpiMetrics
        );
        FamilyHistoryQueryService familyHistoryQueryService = new FamilyHistoryQueryServiceImpl(
            familyRepository,
            childRepository,
            taskRepository,
            shopItemRepository,
            historyRepository,
            purchaseRequestRepository,
            mapper
        );

        this.familyRepository = familyRepository;
        this.childRepository = childRepository;
        this.friendRepository = friendRepository;
        this.objectMapper = mapper;
        this.familyDashboardQueryService = familyDashboardQueryService;
        this.familyHistoryQueryService = familyHistoryQueryService;
        this.familyCommandService = new FamilyCommandServiceImpl(
            familyRepository,
            childRepository,
            familyDashboardQueryService,
            taskRepository,
            shopItemRepository,
            analyticsService,
            mapper
        );
        this.analyticsService = analyticsService;
    }

    @Override
    public OperationResult<FamilyDashboardShellResponse> loadFamilyShellData(String familyId, Integer childId,
                                                                             boolean adminSession) {
        return familyDashboardQueryService.loadFamilyShellData(familyId, childId, adminSession);
    }

    @Override
    public OperationResult<FamilyDashboardDetailResponse> loadFamilyDetailData(String familyId, Integer childId,
                                                                               boolean adminSession) {
        return familyDashboardQueryService.loadFamilyDetailData(familyId, childId, adminSession);
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
        invalidateAnalyticsCache(familyId);
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
        invalidateAnalyticsCache(familyId);
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
        invalidateAnalyticsCache(familyId);
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
        invalidateAnalyticsCache(familyId);
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
        invalidateAnalyticsCache(familyId);
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
        invalidateAnalyticsCache(familyId);
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

        boolean saved = friendRepository.addFriend(childId, friendChildId);
        if (!saved) {
            return failure("FRIEND_ADD_FAILED", "family.friendAddFailed");
        }

        invalidateAnalyticsCache(familyId);
        return OperationResult.success(null);
    }

    @Override
    public OperationResult<List<FriendDto>> getFriendsData(int childId) {
        var friendIds = friendRepository.getFriendChildIds(childId);
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
        return familyHistoryQueryService.getHistory(familyId, childId, page, limit);
    }

    @Override
    public OperationResult<PaginatedRequests> getRequests(String familyId, int page, int limit) {
        return familyHistoryQueryService.getRequests(familyId, page, limit);
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
            invalidateAnalyticsCache(familyId);
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

    private void invalidateAnalyticsCache(String familyId) {
        analyticsService.invalidateCache(familyId);
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
}
