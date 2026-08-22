package com.sashplatonov.earnit.kids.family.application.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.TaskRepository;
import com.sashplatonov.earnit.kids.util.ServiceResults;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.sashplatonov.earnit.kids.family.application.analytics.AnalyticsService;
import com.sashplatonov.earnit.kids.family.application.dashboard.FamilyDashboardQueryService;
@ApplicationScoped
@Slf4j
public class FamilyCommandServiceImpl implements FamilyCommandService {

    private final Supplier<FamilyRepository> familyRepository;
    private final Supplier<ChildRepository> childRepository;
    private final FamilyDashboardQueryService familyDashboardQueryService;
    private final AnalyticsService analyticsService;
    private final FamilyCommandSelectionService selectionService;
    private final FamilyCommandMutationService mutationService;

    public FamilyCommandServiceImpl(FamilyRepository familyRepository,
                                    ChildRepository childRepository,
                                    FamilyDashboardQueryService familyDashboardQueryService,
                                    TaskRepository taskRepository,
                                    ShopItemRepository shopItemRepository,
                                    AnalyticsService analyticsService,
                                    ObjectMapper objectMapper) {
        FamilyCommandPayloadService payloadService = new FamilyCommandPayloadService(objectMapper);
        this.familyRepository = () -> familyRepository;
        this.childRepository = () -> childRepository;
        this.familyDashboardQueryService = familyDashboardQueryService;
        this.analyticsService = analyticsService;
        this.selectionService = new FamilyCommandSelectionService(familyRepository, payloadService);
        this.mutationService = new FamilyCommandMutationService(
            familyRepository, childRepository, taskRepository, shopItemRepository, payloadService);
    }

    @Inject
    public FamilyCommandServiceImpl(Provider<FamilyRepository> familyRepository,
                                    Provider<ChildRepository> childRepository,
                                    FamilyDashboardQueryService familyDashboardQueryService,
                                    TaskRepository taskRepository,
                                    ShopItemRepository shopItemRepository,
                                    AnalyticsService analyticsService,
                                    ObjectMapper objectMapper) {
        FamilyCommandPayloadService payloadService = new FamilyCommandPayloadService(objectMapper);
        this.familyRepository = familyRepository::get;
        this.childRepository = childRepository::get;
        this.familyDashboardQueryService = familyDashboardQueryService;
        this.analyticsService = analyticsService;
        this.selectionService = new FamilyCommandSelectionService(familyRepository.get(), payloadService);
        this.mutationService = new FamilyCommandMutationService(
            familyRepository.get(), childRepository.get(), taskRepository, shopItemRepository, payloadService);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> saveFamilyData(String familyId, Integer childId,
                                                              Map<String, Object> payload,
                                                              boolean adminSession) {
        Optional<Integer> dbIdOpt = familyRepository.get().getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        int familyDbId = dbIdOpt.get();
        List<ChildEntity> children = childRepository.get().getChildren(familyDbId);
        if (children.isEmpty()) {
            return familyDashboardQueryService.loadFamilyData(familyId, childId, adminSession);
        }

        List<ChildEntity> accessibleChildren = selectionService.resolveVisibleChildren(children, adminSession, childId);
        if (accessibleChildren.isEmpty()) {
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        Integer selectedChildId = selectionService.resolveSelectedChildId(
            familyId,
            childId,
            payload,
            accessibleChildren,
            adminSession
        );
        if (selectedChildId != null
            && accessibleChildren.stream().noneMatch(child -> child.getId().equals(selectedChildId))) {
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }

        mutationService.syncFamilyRules(familyId, payload, adminSession);
        mutationService.syncTasks(familyDbId, selectedChildId, payload);
        mutationService.syncShopItems(familyDbId, selectedChildId, payload);
        familyRepository.get().updateLastActivity(familyId);
        analyticsService.invalidateCache(familyId);

        return familyDashboardQueryService.loadFamilyData(familyId, selectedChildId, adminSession);
    }
}
