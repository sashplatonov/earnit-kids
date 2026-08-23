package com.sashplatonov.earnit.kids.family.application.invitation;

import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.domain.model.invitation.ParentEmailInvitationEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.identity.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.identity.domain.model.OAuthInvitationContinuationEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.invitation.ParentEmailInvitationRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.OAuthInvitationContinuationRepository;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.platform.security.SecurityAuditWriter;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParentInvitationServiceImplTest {
    @Mock FamilyRepository familyRepository;
    @Mock ParentEmailInvitationRepository invitationRepository;
    @Mock FamilyParentMembershipRepository membershipRepository;
    @Mock ParentAccountRepository parentAccountRepository;
    @Mock OAuthInvitationContinuationRepository continuationRepository;
    @Mock ParentInvitationEmailSender emailSender;
    @Mock SecurityAuditWriter auditWriter;

    private ParentInvitationService service;
    private ParentInvitationTokenHasher tokenHasher;

    @BeforeEach
    void setUp() {
        tokenHasher = new ParentInvitationTokenHasher("active", "active-secret", "previous", "previous-secret");
        service = new ParentInvitationService(familyRepository, invitationRepository, membershipRepository,
            parentAccountRepository, continuationRepository, emailSender, auditWriter, tokenHasher);
    }

    @Test
    void beginRejectsUnknownTokenWithoutCreatingContinuation() {
        when(invitationRepository.findByDigest(org.mockito.ArgumentMatchers.anyString(), any(String.class)))
            .thenReturn(Optional.empty());

        OperationResult<ParentInvitationService.Continuation> result =
            service.begin("not-a-token", "browser", "/invite/parent");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        org.mockito.Mockito.verify(continuationRepository, org.mockito.Mockito.never()).persist(
            org.mockito.ArgumentMatchers.any(OAuthInvitationContinuationEntity.class));
    }

    @Test
    void createSendsOpaqueLinkAndReturnsPendingInvitation() {
        FamilyEntity family = FamilyEntity.builder().id(7).familyId("family-7")
            .email("owner@example.com").adminPassword("hash").build();
        when(familyRepository.findById("family-7")).thenReturn(Optional.of(family));
        when(invitationRepository.findPending(7, "parent@example.com")).thenReturn(Optional.empty());
        when(parentAccountRepository.findByEmail("parent@example.com")).thenReturn(Optional.empty());

        var result = service.create("family-7", " Parent@Example.com ", "editor", "owner@example.com");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(((OperationResult.Success<?>) result).value()).isNotNull();
        org.mockito.Mockito.verify(emailSender).send(org.mockito.ArgumentMatchers.argThat(email ->
            email.recipient().equals("parent@example.com")
                && email.inviteUrl().startsWith("http://localhost:3000/invite/parent/")
                && email.permission().equals("editor")));
    }

    @Test
    void createRejectsExistingPendingInvitation() {
        FamilyEntity family = FamilyEntity.builder().id(7).familyId("family-7")
            .email("owner@example.com").adminPassword("hash").build();
        ParentEmailInvitationEntity existing = ParentEmailInvitationEntity.builder()
            .id(8).familyId(7).normalizedEmail("parent@example.com")
            .permission(FamilyParentMembershipEntity.Permission.viewer)
            .tokenDigest("digest").expiresAt(Instant.now().plusSeconds(60)).build();
        when(familyRepository.findById("family-7")).thenReturn(Optional.of(family));
        when(invitationRepository.findPending(7, "parent@example.com")).thenReturn(Optional.of(existing));

        var result = service.create("family-7", "parent@example.com", "viewer", "owner@example.com");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<?>) result).errorCode()).isEqualTo("PARENT_INVITATION_EXISTS");
        org.mockito.Mockito.verifyNoInteractions(emailSender);
    }

    @Test
    void beginPersistsShortLivedContinuationForValidInvitation() {
        ParentEmailInvitationEntity invitation = ParentEmailInvitationEntity.builder()
            .id(8).familyId(7).normalizedEmail("parent@example.com")
            .permission(FamilyParentMembershipEntity.Permission.viewer)
            .tokenDigest("digest").expiresAt(Instant.now().plusSeconds(60)).build();
        when(invitationRepository.findByDigest(any(String.class), any(String.class)))
            .thenReturn(Optional.of(invitation));

        var result = service.begin("opaque-token", "browser-binding", "/unsafe");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        org.mockito.Mockito.verify(continuationRepository).persist(any(OAuthInvitationContinuationEntity.class));
    }

    @Test
    void beginAcceptsInvitationIssuedWithImmediatelyPreviousKey() {
        ParentEmailInvitationEntity invitation = ParentEmailInvitationEntity.builder()
            .id(8).familyId(7).normalizedEmail("parent@example.com")
            .permission(FamilyParentMembershipEntity.Permission.viewer)
            .tokenDigest(tokenHasher.digest("opaque-token", "previous"))
            .tokenDigestKeyId("previous")
            .expiresAt(Instant.now().plusSeconds(60)).build();
        when(invitationRepository.findByDigest(tokenHasher.digest("opaque-token", "active"), "active"))
            .thenReturn(Optional.empty());
        when(invitationRepository.findByDigest(tokenHasher.digest("opaque-token", "previous"), "previous"))
            .thenReturn(Optional.of(invitation));

        var result = service.begin("opaque-token", "browser-binding", "/unsafe");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
    }

    @Test
    void hasherRejectsUnknownKeyIdentifier() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> tokenHasher.digest("opaque-token", "retired"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumeOAuthUsesConditionalSingleUseRepositoryOperation() {
        when(continuationRepository.consume(any(), any(String.class), any(Instant.class),
            org.mockito.ArgumentMatchers.eq("parent@example.com"))).thenReturn(1);

        assertThat(service.consumeOAuth(8, "browser-binding", "parent@example.com")).isTrue();
    }

    @Test
    void acceptIsIdempotentAfterInvitationWasConsumed() {
        OAuthInvitationContinuationEntity continuation = OAuthInvitationContinuationEntity.builder()
            .id(4).invitationId(8).browserBindingDigest("ignored")
            .nonceDigest("nonce").issuedAt(Instant.now().minusSeconds(10))
            .expiresAt(Instant.now().plusSeconds(60)).consumedAt(Instant.now())
            .verifiedEmail("parent@example.com").build();
        ParentEmailInvitationEntity invitation = ParentEmailInvitationEntity.builder()
            .id(8).familyId(7).normalizedEmail("parent@example.com")
            .permission(FamilyParentMembershipEntity.Permission.viewer)
            .tokenDigest("digest").expiresAt(Instant.now().plusSeconds(60)).build();
        ParentAccountEntity parent = ParentAccountEntity.builder().id(9).email("parent@example.com")
            .passwordHash("google-only").build();
        when(continuationRepository.findConsumed(any(), any(String.class), any(Instant.class)))
            .thenReturn(Optional.of(continuation));
        when(invitationRepository.findByIdOptional(8)).thenReturn(Optional.of(invitation));
        when(invitationRepository.consume(org.mockito.ArgumentMatchers.eq(8), any(Instant.class))).thenReturn(0);

        var result = service.accept(4, "browser-binding", "parent@example.com");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
    }
}
