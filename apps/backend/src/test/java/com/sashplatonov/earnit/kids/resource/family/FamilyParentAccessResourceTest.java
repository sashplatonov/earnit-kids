package com.sashplatonov.earnit.kids.resource.family;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.service.family.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.service.websocket.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyParentAccessResourceTest {

    @Mock FamilyService familyService;
    @Mock WebSocketNotificationService webSocketNotificationService;
    @Mock FamilyParentAccessService familyParentAccessService;

    private FamilyParentAccessResource resource;

    @BeforeEach
    void setUp() {
        resource = new FamilyParentAccessResource(
            familyService, webSocketNotificationService, familyParentAccessService);
    }

    @Test
    void transferAdmin_passesSignedParentAccountId() {
        when(familyParentAccessService.transferAdmin(7, "fam-1", 42, null))
            .thenReturn(OperationResult.success(null));

        try (Response response = resource.transferAdmin(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "family_admin", 42)), 7)) {
            assertThat(response.getStatus()).isEqualTo(200);
        }

        verify(familyParentAccessService).transferAdmin(7, "fam-1", 42, null);
    }

    @Test
    void removeParent_withoutMembershipPermission_isUnauthorized() {
        try (Response response = resource.removeParent(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "editor", 42)), 7)) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }
}
