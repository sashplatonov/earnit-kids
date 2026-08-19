package com.sashplatonov.earnit.kids.service.family.dashboard;

import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.service.common.ServiceResults;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

import com.sashplatonov.earnit.kids.service.observability.BackendKpiMetrics;
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyDashboardQueryServiceImpl implements FamilyDashboardQueryService {
    private final FamilyDashboardScopeLoader scopeLoader;
    private final FamilyDashboardCatalogLoader catalogLoader;
    private final FamilyDashboardResponseAssembler responseAssembler;
    private final BackendKpiMetrics backendKpiMetrics;

    @Override
    public OperationResult<FamilyDashboardShellResponse> loadFamilyShellData(String familyId, Integer childId,
                                                                             boolean adminSession) {
        return backendKpiMetrics.recordResult("dashboard", "shell", () -> {
            Optional<FamilyDashboardScopeData> scopeOpt = scopeLoader.loadFamilyScope(familyId, childId, adminSession);
            if (scopeOpt.isEmpty()) {
                return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
            }

            FamilyDashboardScopeData scope = scopeOpt.get();
            if (scope.activeChild() == null) {
                return OperationResult.success(responseAssembler.emptyShellResponse(scope.rules(), adminSession));
            }

            FamilyDashboardCatalogContext catalog = catalogLoader.loadCatalogContext(scope.familyDbId(), scope.activeChild().getId());
            return OperationResult.success(responseAssembler.buildShellResponse(scope, catalog, adminSession));
        });
    }

    @Override
    public OperationResult<FamilyDashboardDetailResponse> loadFamilyDetailData(String familyId, Integer childId,
                                                                              boolean adminSession) {
        return backendKpiMetrics.recordResult("dashboard", "detail", () -> {
            Optional<FamilyDashboardScopeData> scopeOpt = scopeLoader.loadFamilyScope(familyId, childId, adminSession);
            if (scopeOpt.isEmpty()) {
                return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
            }

            FamilyDashboardScopeData scope = scopeOpt.get();
            if (scope.activeChild() == null) {
                return OperationResult.success(new FamilyDashboardDetailResponse(List.of(), List.of(), List.of()));
            }

            FamilyDashboardCatalogContext catalog = catalogLoader.loadCatalogContext(scope.familyDbId(), scope.activeChild().getId());
            return OperationResult.success(responseAssembler.buildDetailResponse(scope, catalog, adminSession));
        });
    }

    @Override
    public OperationResult<FamilyDataResponse> loadFamilyData(String familyId, Integer childId, boolean adminSession) {
        return backendKpiMetrics.recordResult("dashboard", "full", () -> {
            Optional<FamilyDashboardScopeData> scopeOpt = scopeLoader.loadFamilyScope(familyId, childId, adminSession);
            if (scopeOpt.isEmpty()) {
                return ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");
            }

            FamilyDashboardScopeData scope = scopeOpt.get();
            if (scope.activeChild() == null) {
                return OperationResult.success(responseAssembler.emptyFamilyDataResponse(scope.rules(), adminSession));
            }

            FamilyDashboardCatalogContext catalog = catalogLoader.loadCatalogContext(scope.familyDbId(), scope.activeChild().getId());
            return OperationResult.success(responseAssembler.buildFamilyDataResponse(scope, catalog, adminSession));
        });
    }
}
