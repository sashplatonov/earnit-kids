package com.sashplatonov.earnit.kids.admin.api.resource;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.AdminDashboardResponse;
import com.sashplatonov.earnit.kids.admin.application.AdminDashboardService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminDashboardResourceTest {

    @Test
    void acceptsAllAndPassesNormalizedPeriod() {
        AdminDashboardService service = mock(AdminDashboardService.class);
        AdminDashboardResponse dashboard = mock(AdminDashboardResponse.class);
        when(service.getDashboard(org.mockito.ArgumentMatchers.any())).thenReturn(dashboard);
        AdminDashboardResource resource = new AdminDashboardResource(service);

        try (Response response = resource.getDashboard(adminContext(), "all")) {
            assertThat(response.getStatus()).isEqualTo(200);
        }

        verify(service).getDashboard(org.mockito.ArgumentMatchers.argThat(period ->
            period.value().equals("all") && period.start().equals(java.time.Instant.EPOCH)));
    }

    @Test
    void rejectsInvalidPeriodBeforeCallingService() {
        AdminDashboardService service = mock(AdminDashboardService.class);
        AdminDashboardResource resource = new AdminDashboardResource(service);

        try (Response response = resource.getDashboard(adminContext(), "invalid")) {
            assertThat(response.getStatus()).isEqualTo(400);
        }

        verifyNoInteractions(service);
    }

    @Test
    void keepsAuthorizationBeforePeriodValidation() {
        AdminDashboardResource resource = new AdminDashboardResource(mock(AdminDashboardService.class));

        assertThatThrownBy(() -> resource.getDashboard(mock(ContainerRequestContext.class), "invalid"))
            .isInstanceOf(WebApplicationException.class)
            .extracting(exception -> ((WebApplicationException) exception).getResponse().getStatus())
            .isEqualTo(401);
    }

    private static ContainerRequestContext adminContext() {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(
            new AuthContext("family-1", null, "admin", "admin@test", "csrf", false, "editor"));
        return context;
    }
}
