package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
class FamilyDashboardResponseAssembler {
    private final FamilyDashboardHydrator hydrator;
    private final FamilyDashboardMapper mapper;
    private final ObjectMapper objectMapper;

    FamilyDashboardShellResponse buildShellResponse(FamilyDashboardScopeData scope,
                                                    FamilyDashboardCatalogContext catalog,
                                                    boolean adminSession) {
        return new FamilyDashboardShellResponse(
            scope.activeChild().getBalance(),
            scope.rules(),
            catalog.tasks(),
            catalog.shopItems(),
            adminSession ? Boolean.TRUE : null,
            scope.visibleChildren().stream().map(child -> mapper.toChildDto(child, objectMapper)).toList(),
            scope.resolvedLastSelectedChildId(),
            scope.activeChild().getId(),
            scope.activeChild().getName(),
            scope.activeChild().getMonthlyLimit(),
            scope.activeChild().getDailyCoinLimit()
        );
    }

    FamilyDashboardDetailResponse buildDetailResponse(FamilyDashboardScopeData scope,
                                                      FamilyDashboardCatalogContext catalog,
                                                      boolean adminSession) {
        return new FamilyDashboardDetailResponse(
            hydrator.loadHistory(scope.familyDbId(), scope.activeChild().getId(), catalog.taskMap(), catalog.shopMap()),
            hydrator.loadRequests(
                scope.familyDbId(),
                scope.activeChild().getId(),
                adminSession,
                catalog.taskMap(),
                catalog.shopMap()
            ),
            hydrator.loadFriends(scope.activeChild().getId())
        );
    }

    FamilyDataResponse buildFamilyDataResponse(FamilyDashboardScopeData scope,
                                               FamilyDashboardCatalogContext catalog,
                                               boolean adminSession) {
        FamilyDashboardShellResponse shell = buildShellResponse(scope, catalog, adminSession);
        FamilyDashboardDetailResponse detail = buildDetailResponse(scope, catalog, adminSession);
        return new FamilyDataResponse(
            shell.balance(),
            shell.rules(),
            shell.tasks(),
            shell.shop(),
            detail.history(),
            detail.requests(),
            detail.friends(),
            shell.isAdmin(),
            shell.children(),
            shell.lastSelectedChildId(),
            shell.childNickname(),
            shell.monthlyLimit(),
            shell.dailyCoinLimit()
        );
    }

    FamilyDataResponse emptyFamilyDataResponse(String rules, boolean adminSession) {
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

    FamilyDashboardShellResponse emptyShellResponse(String rules, boolean adminSession) {
        return new FamilyDashboardShellResponse(
            0,
            rules,
            List.of(),
            List.of(),
            adminSession ? Boolean.TRUE : null,
            List.of(),
            null,
            null,
            null,
            null,
            null
        );
    }
}
