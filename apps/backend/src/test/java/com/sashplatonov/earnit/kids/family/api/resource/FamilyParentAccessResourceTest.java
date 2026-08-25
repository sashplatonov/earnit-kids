package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.family.api.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.family.api.response.ChildDto;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.MembershipStatus;
import com.sashplatonov.earnit.kids.platform.realtime.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.util.List;
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
    private FamilyParentTransferResource transferResource;
    private FamilyInactiveChildrenResource inactiveChildrenResource;

    @BeforeEach
    void setUp() {
        resource = new FamilyParentAccessResource(
            familyService, webSocketNotificationService, familyParentAccessService);
        transferResource = new FamilyParentTransferResource(
            familyService, webSocketNotificationService, familyParentAccessService);
        inactiveChildrenResource = new FamilyInactiveChildrenResource(
            familyService, webSocketNotificationService, familyParentAccessService);
    }

    private static ParentMembershipDto dto(int id, FamilyParentMembershipEntity.Permission permission) {
        return new ParentMembershipDto(
            id, "p" + id + "@example.com", null, null, null, null,
            permission, MembershipStatus.active, null, "pending", null, null, 99, "target");
    }

    @Test
    void transferAdmin_passesSignedParentAccountId() {
        when(familyParentAccessService.transferAdmin(7, "fam-1", 42, null))
            .thenReturn(OperationResult.success(dto(7, FamilyParentMembershipEntity.Permission.editor)));

        try (Response response = transferResource.transferAdmin(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "family_admin", 42)), 7)) {
            assertThat(response.getStatus()).isEqualTo(200);
        }

        verify(familyParentAccessService).transferAdmin(7, "fam-1", 42, null);
    }

    @Test
    void transferAdmin_nonAdminParent_isUnauthorized() {
        try (Response response = transferResource.transferAdmin(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "editor", 42)), 7)) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void transferAdmin_duplicatePendingRequest_returnsConflict() {
        when(familyParentAccessService.transferAdmin(7, "fam-1", 42, null))
            .thenReturn(OperationResult.failure("PARENT_TRANSFER_REQUEST_PENDING_EXISTS", "pending"));

        try (Response response = transferResource.transferAdmin(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "family_admin", 42)), 7)) {
            assertThat(response.getStatus()).isEqualTo(409);
        }
    }

    @Test
    void transferAdmin_otherServiceFailure_returns400() {
        when(familyParentAccessService.transferAdmin(7, "fam-1", 42, null))
            .thenReturn(OperationResult.failure("PARENT_NOT_AUTHORIZED", "nope"));

        try (Response response = transferResource.transferAdmin(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "family_admin", 42)), 7)) {
            assertThat(response.getStatus()).isEqualTo(400);
        }
    }

    @Test
    void transferAdmin_missingAuthContext_isUnauthorized() {
        try (Response response = transferResource.transferAdmin(contextWithAuth(null), 7)) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void acceptTransferRequest_targetParent_returns200() {
        when(familyParentAccessService.acceptTransferRequest(99, "fam-1", 42, null))
            .thenReturn(OperationResult.success(dto(7, FamilyParentMembershipEntity.Permission.family_admin)));

        try (Response response = transferResource.acceptTransferRequest(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "viewer", 42)), 99)) {
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void acceptTransferRequest_nonTargetParent_returns403() {
        when(familyParentAccessService.acceptTransferRequest(99, "fam-1", 42, null))
            .thenReturn(OperationResult.failure("PARENT_MEMBERSHIP_FORBIDDEN", "forbidden"));

        try (Response response = transferResource.acceptTransferRequest(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "viewer", 42)), 99)) {
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    @Test
    void acceptTransferRequest_missingParentAccount_isUnauthorized() {
        try (Response response = transferResource.acceptTransferRequest(contextWithAuth(
            new AuthContext("fam-1", null, "admin", "a@b.c", "csrf", false, "viewer", null)), 99)) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void acceptTransferRequest_missingAuthContext_isUnauthorized() {
        try (Response response = transferResource.acceptTransferRequest(contextWithAuth(null), 99)) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void declineTransferRequest_targetParent_returns200() {
        when(familyParentAccessService.declineTransferRequest(99, "fam-1", 42, null))
            .thenReturn(OperationResult.success(dto(7, FamilyParentMembershipEntity.Permission.editor)));

        try (Response response = transferResource.declineTransferRequest(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "viewer", 42)), 99)) {
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void declineTransferRequest_nonTargetParent_returns403() {
        when(familyParentAccessService.declineTransferRequest(99, "fam-1", 42, null))
            .thenReturn(OperationResult.failure("PARENT_MEMBERSHIP_FORBIDDEN", "forbidden"));

        try (Response response = transferResource.declineTransferRequest(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "viewer", 42)), 99)) {
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    @Test
    void cancelTransferRequest_actorParent_returns200() {
        when(familyParentAccessService.cancelTransferRequest(99, "fam-1", 42, null))
            .thenReturn(OperationResult.success(dto(7, FamilyParentMembershipEntity.Permission.family_admin)));

        try (Response response = transferResource.cancelTransferRequest(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "family_admin", 42)), 99)) {
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void cancelTransferRequest_nonActorParent_returns403() {
        when(familyParentAccessService.cancelTransferRequest(99, "fam-1", 42, null))
            .thenReturn(OperationResult.failure("PARENT_MEMBERSHIP_FORBIDDEN", "forbidden"));

        try (Response response = transferResource.cancelTransferRequest(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "editor", 42)), 99)) {
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    @Test
    void removeParent_withoutMembershipPermission_isUnauthorized() {
        try (Response response = resource.removeParent(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "editor", 42)), 7)) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void listInactiveChildren_admin_returns200() {
        when(familyService.listInactiveChildren("fam-1"))
            .thenReturn(OperationResult.success(List.of(
                new ChildDto(7, "Alice", 10, 0, 0, "default", List.of(), List.of(), List.of(), List.of()))));

        try (Response response = inactiveChildrenResource.listInactiveChildren(contextWithAuth(
            new AuthContext("fam-1", null, "admin", null, "csrf", false, "family_admin", 42)))) {
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void listInactiveChildren_nonAdmin_isUnauthorized() {
        try (Response response = inactiveChildrenResource.listInactiveChildren(contextWithAuth(
            new AuthContext("fam-1", null, "parent", null, "csrf", false, "editor", 42)))) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    private static ContainerRequestContext contextWithAuth(AuthContext auth) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        when(context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY)).thenReturn(auth);
        return context;
    }
}
