package com.sashplatonov.earnit.kids.family.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.family.application.action.FrequencyWindowService;
import com.sashplatonov.earnit.kids.family.application.analytics.AnalyticsService;
import com.sashplatonov.earnit.kids.family.application.analytics.AnalyticsServiceImpl;
import com.sashplatonov.earnit.kids.family.application.command.FamilyCommandService;
import com.sashplatonov.earnit.kids.family.application.command.FamilyCommandServiceImpl;
import com.sashplatonov.earnit.kids.family.application.dashboard.FamilyDashboardCatalogLoader;
import com.sashplatonov.earnit.kids.family.application.dashboard.FamilyDashboardHydrator;
import com.sashplatonov.earnit.kids.family.application.dashboard.FamilyDashboardMapper;
import com.sashplatonov.earnit.kids.family.application.dashboard.FamilyDashboardQueryService;
import com.sashplatonov.earnit.kids.family.application.dashboard.FamilyDashboardQueryServiceImpl;
import com.sashplatonov.earnit.kids.family.application.dashboard.FamilyDashboardResponseAssembler;
import com.sashplatonov.earnit.kids.family.application.dashboard.FamilyDashboardScopeLoader;
import com.sashplatonov.earnit.kids.family.application.history.FamilyHistoryQueryService;
import com.sashplatonov.earnit.kids.family.application.history.FamilyHistoryQueryServiceImpl;
import com.sashplatonov.earnit.kids.family.application.history.FamilyHistoryHydration;
import com.sashplatonov.earnit.kids.family.application.history.HistoryDtoMapper;
import com.sashplatonov.earnit.kids.family.application.history.RelatedEntityHydrator;
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
import com.sashplatonov.earnit.kids.util.TimeProvider;

record FamilyServiceTestComponents(
    FamilyPreferenceService preferences,
    FamilyDashboardQueryService dashboard,
    FamilyHistoryQueryService history,
    FamilyCommandService commands,
    AnalyticsService analytics,
    FamilyChildManagementService children,
    FamilyFriendService friends) {
  static FamilyServiceTestComponents create(
      FamilyRepository familyRepository,
      ChildRepository childRepository,
      TaskRepository taskRepository,
      ShopItemRepository shopItemRepository,
      HistoryRepository historyRepository,
      PurchaseRequestRepository purchaseRequestRepository,
      FriendRepository friendRepository,
      TimeProvider timeProvider,
      BackendKpiMetrics backendKpiMetrics) {
    ObjectMapper mapper = new ObjectMapper();
    AnalyticsService analytics =
        new AnalyticsServiceImpl(
            familyRepository,
            historyRepository,
            taskRepository,
            shopItemRepository,
            timeProvider,
            backendKpiMetrics);
    FamilyDashboardMapper dashboardMapper = FamilyDashboardMapper.INSTANCE;
    FamilyDashboardQueryService dashboard =
        createDashboard(
            familyRepository,
            childRepository,
            taskRepository,
            shopItemRepository,
            historyRepository,
            purchaseRequestRepository,
            friendRepository,
            timeProvider,
            mapper,
            backendKpiMetrics);
    FamilyHistoryQueryService history =
        createHistory(
            childRepository,
            taskRepository,
            shopItemRepository,
            historyRepository,
            purchaseRequestRepository,
            familyRepository,
            mapper,
            dashboardMapper);
    FamilyCommandService commands =
        new FamilyCommandServiceImpl(
            familyRepository,
            childRepository,
            dashboard,
            taskRepository,
            shopItemRepository,
            analytics,
            mapper);
    FamilyPreferenceService preferences =
        new FamilyPreferenceService(familyRepository, childRepository, analytics);
    FamilyChildManagementService children =
        new FamilyChildManagementService(
            childRepository,
            mapper,
            analytics,
            new FamilyOperationGuard(familyRepository),
            new ChildOwnershipService(childRepository));
    FamilyFriendService friends =
        new FamilyFriendService(familyRepository, childRepository, friendRepository, analytics);
    return new FamilyServiceTestComponents(
        preferences, dashboard, history, commands, analytics, children, friends);
  }

  private static FamilyDashboardQueryService createDashboard(
      FamilyRepository familyRepository,
      ChildRepository childRepository,
      TaskRepository taskRepository,
      ShopItemRepository shopItemRepository,
      HistoryRepository historyRepository,
      PurchaseRequestRepository purchaseRequestRepository,
      FriendRepository friendRepository,
      TimeProvider timeProvider,
      ObjectMapper mapper,
      BackendKpiMetrics backendKpiMetrics) {
    FamilyDashboardMapper dashboardMapper = FamilyDashboardMapper.INSTANCE;
    var scopeLoader = new FamilyDashboardScopeLoader(familyRepository, childRepository);
    var catalogLoader =
        new FamilyDashboardCatalogLoader(
            historyRepository,
            taskRepository,
            shopItemRepository,
            purchaseRequestRepository,
            familyRepository,
            timeProvider,
            new FrequencyWindowService(),
            dashboardMapper,
            mapper);
    var hydrator =
        new FamilyDashboardHydrator(
            historyRepository,
            purchaseRequestRepository,
            friendRepository,
            childRepository,
            dashboardMapper,
            new HistoryDtoMapper(dashboardMapper),
            new RelatedEntityHydrator(taskRepository, shopItemRepository, dashboardMapper, mapper));
    var assembler = new FamilyDashboardResponseAssembler(hydrator, dashboardMapper, mapper);
    return new FamilyDashboardQueryServiceImpl(
        scopeLoader, catalogLoader, assembler, backendKpiMetrics);
  }

  private static FamilyHistoryQueryService createHistory(
      ChildRepository childRepository,
      TaskRepository taskRepository,
      ShopItemRepository shopItemRepository,
      HistoryRepository historyRepository,
      PurchaseRequestRepository purchaseRequestRepository,
      FamilyRepository familyRepository,
      ObjectMapper mapper,
      FamilyDashboardMapper dashboardMapper) {
    return new FamilyHistoryQueryServiceImpl(
        childRepository,
        taskRepository,
        shopItemRepository,
        historyRepository,
        purchaseRequestRepository,
        dashboardMapper,
        new FamilyOperationGuard(familyRepository),
        new ChildOwnershipService(childRepository),
        new HistoryDtoMapper(dashboardMapper),
        new FamilyHistoryHydration(
            mapper,
            new RelatedEntityHydrator(taskRepository, shopItemRepository, dashboardMapper, mapper)));
  }
}
