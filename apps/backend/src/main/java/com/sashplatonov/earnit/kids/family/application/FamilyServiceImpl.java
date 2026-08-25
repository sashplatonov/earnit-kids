package com.sashplatonov.earnit.kids.family.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.family.api.request.ChildTheme;
import com.sashplatonov.earnit.kids.family.api.request.GroupOrderSection;
import com.sashplatonov.earnit.kids.family.api.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.family.api.response.ChildDto;
import com.sashplatonov.earnit.kids.family.api.response.ChildInfo;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.family.api.response.FriendDto;
import com.sashplatonov.earnit.kids.family.api.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.family.api.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.family.application.analytics.AnalyticsService;
import com.sashplatonov.earnit.kids.family.application.command.FamilyCommandService;
import com.sashplatonov.earnit.kids.family.application.dashboard.FamilyDashboardQueryService;
import com.sashplatonov.earnit.kids.family.application.history.FamilyHistoryQueryService;
import com.sashplatonov.earnit.kids.family.application.membership.ChildOwnershipService;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyChildManagementService;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyOperationGuard;
import com.sashplatonov.earnit.kids.family.application.notification.FamilyPreferenceService;
import com.sashplatonov.earnit.kids.family.application.social.FamilyFriendService;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.TaskRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.history.HistoryRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.request.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.social.FriendRepository;
import com.sashplatonov.earnit.kids.platform.application.observability.BackendKpiMetrics;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
@ApplicationScoped
@Slf4j
public final class FamilyServiceImpl extends FamilyPreferenceOperationsDelegate
    implements FamilyService {
  private final FamilyDashboardQueryService familyDashboardQueryService;
  private final FamilyHistoryQueryService familyHistoryQueryService;
  private final FamilyCommandService familyCommandService;
  private final AnalyticsService analyticsService;
  private final FamilyChildManagementService familyChildManagementService;
  private final FamilyFriendService familyFriendService;
  @Inject
  public FamilyServiceImpl(
      FamilyRepository familyRepository,
      ChildRepository childRepository,
      FriendRepository friendRepository,
      ObjectMapper objectMapper,
      FamilyDashboardQueryService familyDashboardQueryService,
      FamilyHistoryQueryService familyHistoryQueryService,
      FamilyCommandService familyCommandService,
      AnalyticsService analyticsService) {
    super();
    initializePreferences(new FamilyPreferenceService(familyRepository, childRepository, analyticsService));
    this.familyDashboardQueryService = familyDashboardQueryService;
    this.familyHistoryQueryService = familyHistoryQueryService;
    this.familyCommandService = familyCommandService;
    this.analyticsService = analyticsService;
    this.familyChildManagementService =
        new FamilyChildManagementService(
            childRepository,
            objectMapper,
            analyticsService,
            new FamilyOperationGuard(familyRepository),
            new ChildOwnershipService(childRepository));
    this.familyFriendService =
        new FamilyFriendService(
            familyRepository, childRepository, friendRepository, analyticsService);
  }
  FamilyServiceImpl(
      FamilyRepository familyRepository,
      ChildRepository childRepository,
      TaskRepository taskRepository,
      ShopItemRepository shopItemRepository,
      HistoryRepository historyRepository,
      PurchaseRequestRepository purchaseRequestRepository,
      FriendRepository friendRepository,
      com.sashplatonov.earnit.kids.util.TimeProvider timeProvider,
      BackendKpiMetrics backendKpiMetrics) {
    this(
        FamilyServiceTestComponents.create(
            familyRepository,
            childRepository,
            taskRepository,
            shopItemRepository,
            historyRepository,
            purchaseRequestRepository,
            friendRepository,
            timeProvider,
            backendKpiMetrics));
  }

  private FamilyServiceImpl(FamilyServiceTestComponents components) {
    super();
    initializePreferences(components.preferences());
    this.familyDashboardQueryService = components.dashboard();
    this.familyHistoryQueryService = components.history();
    this.familyCommandService = components.commands();
    this.analyticsService = components.analytics();
    this.familyChildManagementService = components.children();
    this.familyFriendService = components.friends();
  }
  @Override
  public OperationResult<FamilyDashboardShellResponse> loadFamilyShellData(
      String familyId, Integer childId, boolean adminSession) {
    return familyDashboardQueryService.loadFamilyShellData(familyId, childId, adminSession);
  }
  @Override
  public OperationResult<FamilyDashboardDetailResponse> loadFamilyDetailData(
      String familyId, Integer childId, boolean adminSession) {
    return familyDashboardQueryService.loadFamilyDetailData(familyId, childId, adminSession);
  }
  @Override
  public OperationResult<FamilyDataResponse> loadFamilyData(
      String familyId, Integer childId, boolean adminSession) {
    return familyDashboardQueryService.loadFamilyData(familyId, childId, adminSession);
  }
  @Override
  @Transactional
  public OperationResult<FamilyDataResponse> saveFamilyData(
      String familyId, Integer childId, Map<String, Object> payload, boolean adminSession) {
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
  public OperationResult<Void> setChildActive(String familyId, int childId, boolean active) {
    return familyChildManagementService.setChildActive(familyId, childId, active);
  }
  @Override
  public OperationResult<List<ChildDto>> listInactiveChildren(String familyId) {
    return familyChildManagementService.listInactiveChildren(familyId);
  }
  @Override
  public OperationResult<Void> updateNickname(String familyId, int childId, String newName) {
    return familyChildManagementService.updateNickname(familyId, childId, newName);
  }
  @Override
  public OperationResult<Void> updateChildSettings(
      String familyId,
      int childId,
      String name,
      int dailyCoinLimit,
      int monthlyLimit,
      Integer dailyRewardLimit) {
    return familyChildManagementService.updateChildSettings(
        familyId, childId, name, dailyCoinLimit, monthlyLimit, dailyRewardLimit);
  }
  @Override
  public OperationResult<Void> updateChildTheme(String familyId, int childId, ChildTheme theme) {
    return familyChildManagementService.updateChildTheme(familyId, childId, theme);
  }
  @Override
  public OperationResult<Void> updateChildGroupOrder(
      String familyId,
      int childId,
      GroupOrderSection section,
      List<String> groups,
      List<String> hiddenGroups,
      boolean personalOrder) {
    return familyChildManagementService.updateChildGroupOrder(
        familyId, childId, section, groups, hiddenGroups, personalOrder);
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
  public OperationResult<AnalyticsResponse> getAnalyticsData(
      String familyId, Integer childId, String timeframe) {
    return analyticsService.getAnalyticsData(familyId, childId, timeframe);
  }
  @Override
  public OperationResult<PaginatedHistory> getHistory(
      String familyId, int childId, int page, int limit) {
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
}
