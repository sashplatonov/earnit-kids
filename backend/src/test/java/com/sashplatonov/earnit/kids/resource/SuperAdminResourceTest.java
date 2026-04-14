package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.dto.request.ToggleFamilyBlockRequest;
import com.sashplatonov.earnit.kids.service.DatabaseBackupService;
import com.sashplatonov.earnit.kids.service.SuperAdminService;
import com.sashplatonov.earnit.kids.service.SystemDashboardService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminResourceTest {

    @Mock SuperAdminService superAdminService;
    @Mock SystemDashboardService systemDashboardService;
    @Mock DatabaseBackupService databaseBackupService;

    private SuperAdminResource resource;

    @BeforeEach
    void setUp() {
        resource = new SuperAdminResource(superAdminService, systemDashboardService, databaseBackupService);
    }

    @Test
    void getFamilies_requiresSuperAdminRole() {
        assertThat(resource.getFamilies(contextWithAuth(null)).getStatus()).isEqualTo(401);
        assertThat(resource.getFamilies(contextWithAuth(adminAuth())).getStatus()).isEqualTo(403);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFamilies_superAdmin_returnsPayload() {
        when(superAdminService.getFamilies()).thenReturn(List.of(Map.of("id", "fam-1")));

        Response response = resource.getFamilies(contextWithAuth(superAdminAuth()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat((Map<String, Object>) response.getEntity())
            .containsEntry("families", List.of(Map.of("id", "fam-1")));
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
    void regenerateChildToken_superAdmin_returnsTokenPayload() {
        when(superAdminService.regenerateChildToken(10)).thenReturn(OperationResult.success("token-10"));

        Response response = resource.regenerateChildToken(contextWithAuth(superAdminAuth()), 10);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(Map.of("success", true, "token", "token-10"));
    }

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }

    private static AuthContext adminAuth() {
        return new AuthContext("fam-1", null, "admin", "admin@test.com", "csrf");
    }

    private static AuthContext superAdminAuth() {
        return new AuthContext("fam-1", null, "super_admin", "root@test.com", "csrf");
    }
}