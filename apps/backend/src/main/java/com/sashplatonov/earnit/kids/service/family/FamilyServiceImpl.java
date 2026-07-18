package com.sashplatonov.earnit.kids.service.family;

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

import java.util.List;
import java.util.Map;

import com.sashplatonov.earnit.kids.service.analytics.AnalyticsService;
import com.sashplatonov.earnit.kids.service.analytics.AnalyticsServiceImpl;
import com.sashplatonov.earnit.kids.service.family.command.FamilyCommandService;
import com.sashplatonov.earnit.kids.service.family.command.FamilyCommandServiceImpl;
import com.sashplatonov.earnit.kids.service.family.action.FrequencyWindowService;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardCatalogLoader;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardHydrator;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardMapper;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardQueryService;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardQueryServiceImpl;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardResponseAssembler;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardScopeLoader;
import com.sashplatonov.earnit.kids.service.observability.BackendKpiMetrics;
@ApplicationScoped
@Slf4j
public final class FamilyServiceImpl implements FamilyService {
    private final FamilyDashboardQueryService familyDashboardQueryService;
    private final FamilyHistoryQueryService familyHistoryQueryService;
    private final FamilyCommandService familyCommandService;
    private final AnalyticsService analyticsService;
    private final FamilyChildManagementService familyChildManagementService;
    private final FamilyFriendService familyFriendService;
    private final FamilyPreferenceService familyPreferenceService;

    @Inject
    public FamilyServiceImpl(FamilyRepository familyRepository,
                             ChildRepository childRepository,
                             FriendRepository friendRepository,
                             ObjectMapper objectMapper,
                             FamilyDashboardQueryService familyDashboardQueryService,
                             FamilyHistoryQueryService familyHistoryQueryService,
                             FamilyCommandService familyCommandService,
                             AnalyticsService analyticsService) {
        this.familyDashboardQueryService = familyDashboardQueryService;
        this.familyHistoryQueryService = familyHistoryQueryService;
        this.familyCommandService = familyCommandService;
        this.analyticsService = analyticsService;
        this.familyChildManagementService = new FamilyChildManagementService(
            familyRepository,
            childRepository,
            objectMapper,
            analyticsService
        );
        this.familyFriendService = new FamilyFriendService(
            familyRepository,
            childRepository,
            friendRepository,
            analyticsService
        );
        this.familyPreferenceService = new FamilyPreferenceService(
            familyRepository,
            childRepository,
            analyticsService
        );
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
        FamilyDashboardMapper dashboardMapper = FamilyDashboardMapper.INSTANCE;
        FamilyDashboardScopeLoader scopeLoader = new FamilyDashboardScopeLoader(
            familyRepository,
            childRepository
        );
        FamilyDashboardCatalogLoader catalogLoader = new FamilyDashboardCatalogLoader(
            historyRepository,
            taskRepository,
            shopItemRepository,
            purchaseRequestRepository,
            familyRepository,
            timeProvider,
            new FrequencyWindowService(),
            dashboardMapper,
            mapper
        );
        FamilyDashboardHydrator hydrator = new FamilyDashboardHydrator(
            historyRepository,
            purchaseRequestRepository,
            friendRepository,
            childRepository,
            taskRepository,
            shopItemRepository,
            dashboardMapper,
            mapper
        );
        FamilyDashboardResponseAssembler responseAssembler = new FamilyDashboardResponseAssembler(
            hydrator,
            dashboardMapper,
            mapper
        );
        FamilyDashboardQueryService familyDashboardQueryService = new FamilyDashboardQueryServiceImpl(
            scopeLoader,
            catalogLoader,
            responseAssembler,
            backendKpiMetrics
        );
        FamilyHistoryQueryService familyHistoryQueryService = new FamilyHistoryQueryServiceImpl(
            familyRepository,
            childRepository,
            taskRepository,
            shopItemRepository,
            historyRepository,
            purchaseRequestRepository,
            dashboardMapper,
            mapper
        );
        FamilyCommandService familyCommandService = new FamilyCommandServiceImpl(
            familyRepository,
            childRepository,
            familyDashboardQueryService,
            taskRepository,
            shopItemRepository,
            analyticsService,
            mapper
        );

        this.familyDashboardQueryService = familyDashboardQueryService;
        this.familyHistoryQueryService = familyHistoryQueryService;
        this.familyCommandService = familyCommandService;
        this.analyticsService = analyticsService;
        this.familyChildManagementService = new FamilyChildManagementService(
            familyRepository,
            childRepository,
            mapper,
            analyticsService
        );
        this.familyFriendService = new FamilyFriendService(
            familyRepository,
            childRepository,
            friendRepository,
            analyticsService
        );
        this.familyPreferenceService = new FamilyPreferenceService(
            familyRepository,
            childRepository,
            analyticsService
        );
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
        return familyChildManagementService.createChild(familyId, childName);
    }

    @Override
    public OperationResult<Void> deleteChild(String familyId, int childId) {
        return familyChildManagementService.deleteChild(familyId, childId);
    }

    @Override
    public OperationResult<Void> updateNickname(String familyId, int childId, String newName) {
        return familyChildManagementService.updateNickname(familyId, childId, newName);
    }

    @Override
    public OperationResult<Void> updateChildSettings(String familyId, int childId,
                                                      String name, int dailyCoinLimit,
                                                      int monthlyLimit) {
        return familyChildManagementService.updateChildSettings(familyId, childId, name, dailyCoinLimit, monthlyLimit);
    }

    @Override
    public OperationResult<Void> updateChildTheme(String familyId, int childId, ChildTheme theme) {
        return familyChildManagementService.updateChildTheme(familyId, childId, theme);
    }

    @Override
    public OperationResult<Void> updateChildGroupOrder(String familyId, int childId,
                                                       GroupOrderSection section, List<String> groups,
                                                       boolean personalOrder) {
        return familyChildManagementService.updateChildGroupOrder(familyId, childId, section, groups, personalOrder);
    }

    @Override
    public OperationResult<List<FriendDto>> searchByNickname(String nickname, int excludeChildId) {
        return familyFriendService.searchByNickname(nickname, excludeChildId);
    }

    @Override
    public OperationResult<Void> addFriend(String familyId, int childId, int friendChildId) {
        return familyFriendService.addFriend(familyId, childId, friendChildId);
    }

    @Override
    public OperationResult<List<FriendDto>> getFriendsData(int childId) {
        return familyFriendService.getFriendsData(childId);
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
        return familyChildManagementService.getChildLoginLink(familyId, childId);
    }

    @Override
    public OperationResult<String> regenerateChildToken(String familyId, int childId) {
        return familyChildManagementService.regenerateChildToken(familyId, childId);
    }

    @Override
    public OperationResult<Void> updatePreference(String familyId, FamilyPreferenceKey key, Object value) {
        return familyPreferenceService.updatePreference(familyId, key, value);
    }
}
