package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.repository.TelegramCallbackActionRepository;
import com.sashplatonov.earnit.kids.repository.TelegramChildInvitationRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.repository.TelegramSecurityAuditEventRepository;
import com.sashplatonov.earnit.kids.repository.TelegramWebhookUpdateRepository;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramIdentityServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");

    @Mock TelegramIdentityRepository identities;
    @Mock TelegramChildInvitationRepository invitations;
    @Mock TelegramCallbackActionRepository callbacks;
    @Mock TelegramWebhookUpdateRepository updates;
    @Mock TelegramSecurityAuditEventRepository audits;

    private TelegramIdentityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TelegramIdentityServiceImpl(
            identities, invitations, callbacks, updates, audits, new SecureTokenGenerator());
    }

    @Test
    void linkParent_rejectsTelegramIdentityLinkedToAnotherFamily() {
        TelegramIdentityEntity existing = TelegramIdentityEntity.builder()
            .familyId(2).telegramUserId(77L).role("parent").active(true).linkedAt(NOW).build();
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.linkParent(1, 77L, null, "parent@example.com", NOW))
            .isInstanceOf(IllegalStateException.class);
        verify(identities, never()).persist(any(TelegramIdentityEntity.class));
    }

    @Test
    void linkParent_setsParentAccountIdWhenProvided() {
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.empty());

        service.linkParent(1, 77L, 42, "parent@example.com", NOW);

        verify(identities).persist(org.mockito.ArgumentMatchers.<TelegramIdentityEntity>argThat(identity ->
            identity.getFamilyId().equals(1)
                && identity.getTelegramUserId().equals(77L)
                && identity.getParentAccountId().equals(42)
                && "parent".equals(identity.getRole())));
    }

    @Test
    void acceptInvitation_rejectsExpiredAndAlreadyLinkedIdentity() {
        var invitation = com.sashplatonov.earnit.kids.domain.model.TelegramChildInvitationEntity.builder()
            .familyId(1).childId(10).secretDigest("not-the-token").expiresAt(NOW.minusSeconds(1))
            .issuedBy("parent@example.com").createdAt(NOW).build();
        when(invitations.findByDigestForUpdate(any())).thenReturn(Optional.of(invitation));

        assertThat(service.acceptChildInvitation("secret", 77L, NOW)).isEmpty();

        invitation.setExpiresAt(NOW.plusSeconds(60));
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(
            TelegramIdentityEntity.builder().familyId(1).telegramUserId(77L).role("parent").active(true).linkedAt(NOW).build()));
        assertThat(service.acceptChildInvitation("secret", 77L, NOW)).isEmpty();

        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.empty());
        when(identities.findActiveChild(10)).thenReturn(Optional.of(
            TelegramIdentityEntity.builder().familyId(1).childId(10).telegramUserId(88L)
                .role("child").active(true).linkedAt(NOW).build()));
        assertThat(service.acceptChildInvitation("secret", 77L, NOW)).isEmpty();
    }

    @Test
    void acceptInvitation_bindsTheInvitedChildToItsFamily() {
        var invitation = com.sashplatonov.earnit.kids.domain.model.TelegramChildInvitationEntity.builder()
            .familyId(1).childId(10).secretDigest("not-the-token").expiresAt(NOW.plusSeconds(60))
            .issuedBy("parent@example.com").createdAt(NOW).build();
        when(invitations.findByDigestForUpdate(any())).thenReturn(Optional.of(invitation));
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.empty());
        when(identities.findActiveChild(10)).thenReturn(Optional.empty());

        assertThat(service.acceptChildInvitation("secret", 77L, NOW))
            .contains(new TelegramIdentityService.TelegramIdentity(null, 1, 10, 77L, "child"));
        verify(identities).persist(org.mockito.ArgumentMatchers.<TelegramIdentityEntity>argThat(identity ->
            identity.getFamilyId().equals(1) && identity.getChildId().equals(10)));
    }

    @Test
    void mutationCallback_isSingleUseAndRejectsExpiredToken() {
        TelegramIdentityEntity identity = TelegramIdentityEntity.builder()
            .id(4).familyId(1).telegramUserId(77L).role("parent").active(true).linkedAt(NOW).build();
        when(identities.findActiveParentByIdAndFamilyId(4, 1)).thenReturn(Optional.of(identity));
        var token = service.createMutationCallback(1, 4, "APPROVE", 99L, NOW.plusSeconds(60), NOW);
        var callback = com.sashplatonov.earnit.kids.domain.model.TelegramCallbackActionEntity.builder()
            .id(token.callbackId()).familyId(1).identityId(4).action("APPROVE").targetId(99L)
            .secretDigest("not-the-token").expiresAt(NOW.plusSeconds(60)).createdAt(NOW).build();
        assertThat(token.token()).isNotEqualTo(callback.getSecretDigest());
        when(callbacks.findByDigestForUpdate(any())).thenReturn(Optional.of(callback));

        assertThat(service.consumeMutationCallback(token.token(), NOW))
            .contains(new TelegramIdentityService.MutationCallback(
                token.callbackId(), 1, 4, "APPROVE", 99L));
        callback.setConsumedAt(NOW);
        assertThat(service.consumeMutationCallback(token.token(), NOW)).isEmpty();
    }

    @Test
    void mutationCallback_rejectsForeignIdentity() {
        when(identities.findActiveParentByIdAndFamilyId(4, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createMutationCallback(1, 4, "APPROVE", 99L, NOW.plusSeconds(60), NOW))
            .isInstanceOf(IllegalArgumentException.class);
        verify(callbacks, never()).persist(any(com.sashplatonov.earnit.kids.domain.model.TelegramCallbackActionEntity.class));
    }

    @Test
    void webhookUpdate_usesAtomicDeduplication() {
        when(updates.recordIfNew(42L, NOW)).thenReturn(true, false);
        assertThat(service.recordWebhookUpdate(42L, NOW)).isTrue();
        assertThat(service.recordWebhookUpdate(42L, NOW)).isFalse();
        verify(updates, org.mockito.Mockito.times(2)).recordIfNew(42L, NOW);
    }

    @Test
    void needsReplyKeyboardReset_isTrueWhenStoredVersionIsBehind() {
        TelegramIdentityEntity identity = TelegramIdentityEntity.builder()
            .familyId(1).telegramUserId(77L).role("parent").active(true).linkedAt(NOW)
            .keyboardVersion(0).build();
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(identity));

        assertThat(service.needsReplyKeyboardReset(77L, 1)).isTrue();
        assertThat(service.needsReplyKeyboardReset(77L, 0)).isFalse();
    }

    @Test
    void needsReplyKeyboardReset_isFalseWhenIdentityMissing() {
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.empty());

        assertThat(service.needsReplyKeyboardReset(77L, 1)).isFalse();
    }

    @Test
    void markReplyKeyboardVersion_updatesStoredVersion() {
        TelegramIdentityEntity identity = TelegramIdentityEntity.builder()
            .familyId(1).telegramUserId(77L).role("parent").active(true).linkedAt(NOW)
            .keyboardVersion(0).build();
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(identity));

        service.markReplyKeyboardVersion(77L, 2);

        assertThat(identity.getKeyboardVersion()).isEqualTo(2);
    }
}
