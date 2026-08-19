package com.sashplatonov.earnit.kids.service.family.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.service.common.ServiceResults;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sashplatonov.earnit.kids.service.analytics.AnalyticsService;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardQueryService;
@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyCommandServiceImpl implements FamilyCommandService {

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
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
        this.familyRepository = familyRepository;
        this.childRepository = childRepository;
        this.familyDashboardQueryService = familyDashboardQueryService;
        this.analyticsService = analyticsService;
        this.selectionService = new FamilyCommandSelectionService(familyRepository, payloadService);
        this.mutationService = new FamilyCommandMutationService(
            familyRepository, childRepository, taskRepository, shopItemRepository, payloadService);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> saveFamilyData(String familyId, Integer childId,
                                                              Map<String, Object> payload,
                                                              boolean adminSession) {
        Optional<Integer> dbIdOpt = familyRepository.getDbId(familyId);
        if (dbIdOpt.isEmpty()) {
            return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
        }

        int familyDbId = dbIdOpt.get();
        List<ChildEntity> children = childRepository.getChildren(familyDbId);
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
        familyRepository.updateLastActivity(familyId);
        analyticsService.invalidateCache(familyId);

        return familyDashboardQueryService.loadFamilyData(familyId, selectedChildId, adminSession);
    }
}
