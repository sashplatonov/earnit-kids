package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.dto.request.ToggleFamilyBlockRequest;
import com.sashplatonov.earnit.kids.dto.response.ApplicationLogsResponse;
import com.sashplatonov.earnit.kids.dto.response.DatabaseHealthResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.i18n.RequestLocaleHolder;
import com.sashplatonov.earnit.kids.service.SuperAdminService;
import com.sashplatonov.earnit.kids.service.SystemDashboardService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminResourceTest {

    @Mock SuperAdminService superAdminService;
    @Mock SystemDashboardService systemDashboardService;

    private SuperAdminResource resource;
    private SystemDashboardResource systemDashboardResource;

    @BeforeEach
    void setUp() {
        RequestLocaleHolder.set("en");
        resource = new SuperAdminResource(superAdminService);
        systemDashboardResource = new SystemDashboardResource(systemDashboardService);
    }

    @AfterEach
    void tearDown() {
        RequestLocaleHolder.clear();
    }

    @Test
    void getFamilies_requiresSuperAdminRole() {
        assertThat(resource.getFamilies(contextWithAuth(null)).getStatus()).isEqualTo(401);
        assertThat(resource.getFamilies(contextWithAuth(adminAuth())).getStatus()).isEqualTo(403);
    }

    @Test
    void getFamilies_superAdmin_returnsPayload() {
        when(superAdminService.getFamilies()).thenReturn(
            new com.sashplatonov.earnit.kids.dto.response.SuperAdminFamiliesResponse(
                List.of(new com.sashplatonov.earnit.kids.dto.response.SuperAdminFamiliesResponse.FamilySummary(
                    "fam-1",
                    "a@test.com",
                    "2026-07-09T00:00:00Z",
                    "2026-07-09T00:00:00Z",
                    false,
                    1,
                    1,
                    1,
                    List.of()
                ))
            )
        );

        Response response = resource.getFamilies(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(((com.sashplatonov.earnit.kids.dto.response.SuperAdminFamiliesResponse) response.getEntity())
            .families())
            .hasSize(1);
    }

    @Test
    void getFamilyDetails_notFound_returns404() {
        when(superAdminService.getFamilyDetails("missing")).thenReturn(null);

        Response response = resource.getFamilyDetails(contextWithAuth(superAdminAuth()), "missing");

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void getFamilyDetails_superAdmin_returnsData() {
        when(superAdminService.getFamilyDetails("fam-1")).thenReturn(
            new com.sashplatonov.earnit.kids.dto.response.SuperAdminFamilyDetailsResponse(
                "fam-1",
                new com.sashplatonov.earnit.kids.dto.response.SuperAdminFamilyDetailsResponse.FamilyInfo(
                    "fam-1",
                    "a@test.com",
                    "2026-07-09T00:00:00Z",
                    "2026-07-09T00:00:00Z",
                    false,
                    1,
                    List.of(),
                    1000
                ),
                new com.sashplatonov.earnit.kids.dto.response.SuperAdminFamilyDetailsResponse.FamilyData(
                    0,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
                )
            )
        );

        Response response = resource.getFamilyDetails(contextWithAuth(superAdminAuth()), "fam-1");

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void toggleFamilyBlock_superAdmin_delegatesToService() {
        when(superAdminService.setFamilyBlocked("fam-1", true)).thenReturn(true);

        Response response = resource.toggleFamilyBlock(
            contextWithAuth(superAdminAuth()),
            "fam-1",
            new ToggleFamilyBlockRequest(true)
        );

        assertThat(response.getStatus()).isEqualTo(200);
        verify(superAdminService).setFamilyBlocked("fam-1", true);
    }

    @Test
    void toggleFamilyBlock_familyNotFound_returns404() {
        when(superAdminService.setFamilyBlocked("missing", false)).thenReturn(false);

        Response response = resource.toggleFamilyBlock(
            contextWithAuth(superAdminAuth()),
            "missing",
            new ToggleFamilyBlockRequest(false)
        );

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void toggleFamilyBlock_nullRequest_returns400() {
        Response response = resource.toggleFamilyBlock(contextWithAuth(superAdminAuth()), "fam-1", null);
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void regenerateFamilyToken_superAdmin_returnsToken() {
        when(superAdminService.regenerateFamilyToken("fam-1")).thenReturn(OperationResult.success("new-token"));

        Response response = resource.regenerateFamilyToken(contextWithAuth(superAdminAuth()), "fam-1");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(((com.sashplatonov.earnit.kids.dto.response.TokenResponse) response.getEntity()).token())
            .isEqualTo("new-token");
    }

    @Test
    void regenerateFamilyToken_failure_returns400() {
        when(superAdminService.regenerateFamilyToken("fam-1")).thenReturn(OperationResult.failure("not found"));

        Response response = resource.regenerateFamilyToken(contextWithAuth(superAdminAuth()), "fam-1");

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void regenerateChildToken_superAdmin_returnsTokenPayload() {
        when(superAdminService.regenerateChildToken(10)).thenReturn(OperationResult.success("token-10"));

        Response response = resource.regenerateChildToken(contextWithAuth(superAdminAuth()), 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(((com.sashplatonov.earnit.kids.dto.response.TokenResponse) response.getEntity()).token())
            .isEqualTo("token-10");
    }

    @Test
    void setFamilyPassword_superAdmin_returnsOk() {
        when(superAdminService.setFamilyPassword("fam-1", "newpass123")).thenReturn(OperationResult.success(null));

        Response response = resource.setFamilyPassword(
            contextWithAuth(superAdminAuth()),
            "fam-1",
            new com.sashplatonov.earnit.kids.dto.request.SetPasswordRequest("newpass123")
        );

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void setFamilyPassword_missingFamily_returns404() {
        when(superAdminService.setFamilyPassword("missing", "newpass123"))
            .thenReturn(OperationResult.failure("FAMILY_NOT_FOUND", BackendMessages.message("family.familyNotFound")));

        Response response = resource.setFamilyPassword(
            contextWithAuth(superAdminAuth()),
            "missing",
            new com.sashplatonov.earnit.kids.dto.request.SetPasswordRequest("newpass123")
        );

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void getBaseData_superAdmin_returnsData() {
        when(superAdminService.getBaseData()).thenReturn(java.util.Map.of("tasks", List.of(), "products", List.of()));

        Response response = resource.getBaseData(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void getBaseData_requiresSuperAdmin() {
        assertThat(resource.getBaseData(contextWithAuth(adminAuth())).getStatus()).isEqualTo(403);
    }

    @Test
    void saveBaseData_success_returnsOk() {
        when(superAdminService.saveBaseData(java.util.Map.of("tasks", List.of()))).thenReturn(true);

        Response response = resource.saveBaseData(contextWithAuth(superAdminAuth()), java.util.Map.of("tasks", List.of()));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void saveBaseData_failure_returns500() {
        when(superAdminService.saveBaseData(java.util.Map.of())).thenReturn(false);

        Response response = resource.saveBaseData(contextWithAuth(superAdminAuth()), java.util.Map.of());

        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    void getSystemOverview_superAdmin_returnsPayload() {
        when(systemDashboardService.getOverview()).thenReturn(new com.sashplatonov.earnit.kids.dto.response.SystemOverviewResponse(
            new com.sashplatonov.earnit.kids.dto.response.SystemOverviewResponse.ProcessStats(100L, 50L, 100L),
            new com.sashplatonov.earnit.kids.dto.response.SystemOverviewResponse.OperatingSystemStats(1.0, 1.0, 1.0, 8),
            "2026-07-09T00:00:00Z"
        ));

        Response response = systemDashboardResource.getSystemOverview(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void getSystemOverview_requiresSuperAdmin() {
        assertThat(systemDashboardResource.getSystemOverview(contextWithAuth(null)).getStatus()).isEqualTo(401);
        assertThat(systemDashboardResource.getSystemOverview(contextWithAuth(adminAuth())).getStatus()).isEqualTo(403);
    }

    @Test
    void getDatabaseHealth_superAdmin_returnsPayload() {
        when(systemDashboardService.getDbHealth()).thenReturn(
            new DatabaseHealthResponse(new DatabaseHealthResponse.DbHealth(true, 1L, null))
        );

        Response response = systemDashboardResource.getDatabaseHealth(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void getHttpMetrics_superAdmin_returnsPayload() {
        when(systemDashboardService.getHttpMetrics()).thenReturn(
            new com.sashplatonov.earnit.kids.dto.response.HttpMetricsResponse(
                new com.sashplatonov.earnit.kids.dto.response.HttpMetricsResponse.HttpMetricsSummary(1L, 0L, 0.0, 1L),
                List.of()
            )
        );

        Response response = systemDashboardResource.getHttpMetrics(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void getLogs_superAdmin_forwardslevelAndLimit() {
        when(systemDashboardService.getLogs("error", 50)).thenReturn(new ApplicationLogsResponse(List.of()));

        Response response = systemDashboardResource.getLogs(contextWithAuth(superAdminAuth()), "error", 50);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(systemDashboardService).getLogs("error", 50);
    }

    @Test
    void getLogs_nullParams_usesDefaults() {
        when(systemDashboardService.getLogs("all", 100)).thenReturn(new ApplicationLogsResponse(List.of()));

        Response response = systemDashboardResource.getLogs(contextWithAuth(superAdminAuth()), null, null);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(systemDashboardService).getLogs("all", 100);
    }

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }

    private static AuthContext adminAuth() {
        return new AuthContext("fam-1", null, "admin", "admin@test.com", "csrf", false, "family_admin");
    }

    private static AuthContext superAdminAuth() {
        return new AuthContext("fam-1", null, "admin", "root@test.com", "csrf", true, "family_admin");
    }
}
