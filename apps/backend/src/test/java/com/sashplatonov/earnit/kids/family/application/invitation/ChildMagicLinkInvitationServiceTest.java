package com.sashplatonov.earnit.kids.family.application.invitation;

import com.sashplatonov.earnit.kids.config.auth.JwtCompatibilityConfig;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.domain.model.invitation.ChildMagicLinkInvitationEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.invitation.ChildMagicLinkInvitationRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChildMagicLinkInvitationServiceTest {
    private final FamilyRepository families = mock(FamilyRepository.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final ChildMagicLinkInvitationRepository invitations = mock(ChildMagicLinkInvitationRepository.class);
    private final SecureTokenGenerator tokens = mock(SecureTokenGenerator.class);
    private final JwtCompatibilityConfig jwt = () -> "test-secret";
    private final ChildMagicLinkInvitationService service =
        new ChildMagicLinkInvitationService(families, children, invitations, tokens, jwt);

    @Test
    void issueRejectsUnknownInactiveAndForeignChildren() {
        when(families.findById("missing")).thenReturn(Optional.empty());
        assertFailure(service.issue("missing", 1), "CHILD_NOT_FOUND");

        FamilyEntity family = family(7, "family-1");
        when(families.findById("family-1")).thenReturn(Optional.of(family));
        when(children.findByIdOptional(1)).thenReturn(Optional.empty());
        assertFailure(service.issue("family-1", 1), "CHILD_NOT_FOUND");

        ChildEntity foreign = child(1, 8, "ACTIVE");
        when(children.findByIdOptional(1)).thenReturn(Optional.of(foreign));
        assertFailure(service.issue("family-1", 1), "CHILD_NOT_FOUND");

        ChildEntity inactive = child(1, 7, "ARCHIVED");
        when(children.findByIdOptional(1)).thenReturn(Optional.of(inactive));
        assertFailure(service.issue("family-1", 1), "CHILD_NOT_FOUND");
    }

    @Test
    void issueRevokesPendingAndStoresDigestAndExpiry() {
        when(families.findById("family-1")).thenReturn(Optional.of(family(7, "family-1")));
        when(children.findByIdOptional(1)).thenReturn(Optional.of(child(1, 7, "ACTIVE")));
        when(tokens.generateHexToken(32)).thenReturn("raw-token");

        OperationResult<String> result = service.issue("family-1", 1);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(((OperationResult.Success<String>) result).value()).isEqualTo("raw-token");
        verify(invitations).revokePending(eq(7), eq(1), any(Instant.class));
        ArgumentCaptor<ChildMagicLinkInvitationEntity> captor = ArgumentCaptor.forClass(ChildMagicLinkInvitationEntity.class);
        verify(invitations).persist(captor.capture());
        ChildMagicLinkInvitationEntity stored = captor.getValue();
        assertThat(stored.getFamilyId()).isEqualTo(7);
        assertThat(stored.getChildId()).isEqualTo(1);
        assertThat(stored.getTokenDigest()).isNotNull();
        assertThat(stored.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void revokeAndStatusHandleMissingAndExistingInvitations() {
        when(families.findById("family-1")).thenReturn(Optional.of(family(7, "family-1")));
        when(invitations.revoke(eq(7), eq(1), any(Instant.class))).thenReturn(0, 1);
        assertFailure(service.revoke("family-1", 1), "CHILD_INVITATION_NOT_FOUND");
        assertThat(service.revoke("family-1", 1)).isInstanceOf(OperationResult.Success.class);

        when(children.findByIdOptional(1)).thenReturn(Optional.empty());
        assertFailure(service.status("family-1", 1), "CHILD_NOT_FOUND");
        when(children.findByIdOptional(1)).thenReturn(Optional.of(child(1, 7, "ACTIVE")));
        ChildMagicLinkInvitationEntity invitation = ChildMagicLinkInvitationEntity.builder()
            .id(4).status(ChildMagicLinkInvitationEntity.Status.pending)
            .expiresAt(Instant.now().plusSeconds(60)).build();
        when(invitations.findByChild(7, 1)).thenReturn(List.of(invitation));
        assertThat(service.status("family-1", 1).isSuccess()).isTrue();
    }

    @Test
    void consumeRejectsBlankExpiredConsumedAndFailedAtomicConsume() {
        assertFailure(service.consume(" "), "CHILD_INVITATION_INVALID");
        when(invitations.findByDigest(any())).thenReturn(Optional.empty());
        assertFailure(service.consume("token"), "CHILD_INVITATION_INVALID");
        ChildMagicLinkInvitationEntity expired = invitation(1, Instant.now().minusSeconds(1),
            ChildMagicLinkInvitationEntity.Status.pending);
        when(invitations.findByDigest(any())).thenReturn(Optional.of(expired));
        assertFailure(service.consume("token"), "CHILD_INVITATION_INVALID");
        expired.setStatus(ChildMagicLinkInvitationEntity.Status.consumed);
        assertFailure(service.consume("token"), "CHILD_INVITATION_INVALID");
        expired.setStatus(ChildMagicLinkInvitationEntity.Status.pending);
        expired.setExpiresAt(Instant.now().plusSeconds(60));
        when(invitations.consume(eq(1), any(Instant.class))).thenReturn(0);
        assertFailure(service.consume("token"), "CHILD_INVITATION_INVALID");
    }

    @Test
    void consumeReturnsChildAuthPayloadOnlyForActiveChildAndExistingFamily() {
        ChildMagicLinkInvitationEntity invitation = invitation(1, Instant.now().plusSeconds(60),
            ChildMagicLinkInvitationEntity.Status.pending);
        when(invitations.findByDigest(any())).thenReturn(Optional.of(invitation));
        when(invitations.consume(eq(1), any(Instant.class))).thenReturn(1);
        when(children.findByIdOptional(1)).thenReturn(Optional.of(child(1, 7, "ACTIVE")));
        when(families.findByIdOptional(7)).thenReturn(Optional.of(family(7, "family-1")));
        OperationResult<?> result = service.consume("token");
        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(((OperationResult.Success<?>) result).value().toString()).contains("family-1");
    }

    @Test
    void consumeRejectsMissingOrInactiveLinkedRecords() {
        ChildMagicLinkInvitationEntity invitation = invitation(1, Instant.now().plusSeconds(60),
            ChildMagicLinkInvitationEntity.Status.pending);
        when(invitations.findByDigest(any())).thenReturn(Optional.of(invitation));
        when(invitations.consume(eq(1), any(Instant.class))).thenReturn(1);
        when(children.findByIdOptional(1)).thenReturn(Optional.empty());
        when(families.findByIdOptional(7)).thenReturn(Optional.of(family(7, "family-1")));
        assertFailure(service.consume("token"), "CHILD_INVITATION_INVALID");

        when(children.findByIdOptional(1)).thenReturn(Optional.of(child(1, 7, "ARCHIVED")));
        assertFailure(service.consume("token"), "CHILD_INVITATION_INVALID");
        when(children.findByIdOptional(1)).thenReturn(Optional.of(child(1, 7, "ACTIVE")));
        when(families.findByIdOptional(7)).thenReturn(Optional.empty());
        assertFailure(service.consume("token"), "CHILD_INVITATION_INVALID");
    }

    private static FamilyEntity family(int id, String familyId) {
        return FamilyEntity.builder().id(id).familyId(familyId).email("parent@test").adminPassword("pw").build();
    }

    private static ChildEntity child(int id, int familyId, String status) {
        return ChildEntity.builder().id(id).familyDbId(familyId).name("Child").status(status).build();
    }

    private static ChildMagicLinkInvitationEntity invitation(int id, Instant expiry,
                                                              ChildMagicLinkInvitationEntity.Status status) {
        return ChildMagicLinkInvitationEntity.builder().id(id).childId(1).familyId(7)
            .status(status).expiresAt(expiry).build();
    }

    private static void assertFailure(OperationResult<?> result, String code) {
        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<?>) result).errorCode()).isEqualTo(code);
    }
}
