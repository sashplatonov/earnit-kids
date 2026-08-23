package com.sashplatonov.earnit.kids.security;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.family.api.request.UpdateParentMembershipRequest;
import com.sashplatonov.earnit.kids.family.api.resource.FamilyParentAccessResource;
import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.platform.realtime.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkspaceAccessAuthorizationTest {

    @Test
    void activeFamilyComesFromSessionAndRequestCannotSwapIt() {
        FamilyParentAccessService parentAccess = mock(FamilyParentAccessService.class);
        FamilyParentAccessResource resource = new FamilyParentAccessResource(
            mock(FamilyService.class), mock(WebSocketNotificationService.class), parentAccess);
        ContainerRequestContext context = contextWith(new AuthContext(
            "family-a", null, "admin", "admin@example.test", "csrf", false, "family_admin", 7));
        when(parentAccess.updateMembership(42, "viewer", "family-a"))
            .thenReturn(OperationResult.success(null));

        Response response = resource.updateParent(context, 42, new UpdateParentMembershipRequest("viewer"));

        assertThat(response.getStatus()).isNotEqualTo(401);
        verify(parentAccess).updateMembership(42, "viewer", "family-a");
    }

    @Test
    void viewerCannotManageMembershipsOrChangePermissions() {
        FamilyParentAccessService parentAccess = mock(FamilyParentAccessService.class);
        FamilyParentAccessResource resource = new FamilyParentAccessResource(
            mock(FamilyService.class), mock(WebSocketNotificationService.class), parentAccess);

        Response response = resource.updateParent(
            contextWith(new AuthContext("family-a", null, "admin", "viewer@example.test", "csrf", false, "viewer", 8)),
            42, new UpdateParentMembershipRequest("family_admin"));

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(parentAccess);
    }

    @Test
    void childCannotOperateOnAnotherChildOrFamily() {
        FamilyParentAccessService parentAccess = mock(FamilyParentAccessService.class);
        FamilyParentAccessResource resource = new FamilyParentAccessResource(
            mock(FamilyService.class), mock(WebSocketNotificationService.class), parentAccess);

        Response response = resource.listParents(
            contextWith(new AuthContext("family-a", 10, "child", "child@example.test", "csrf", false, "child")));

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(parentAccess);
    }

    private static ContainerRequestContext contextWith(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }
}
