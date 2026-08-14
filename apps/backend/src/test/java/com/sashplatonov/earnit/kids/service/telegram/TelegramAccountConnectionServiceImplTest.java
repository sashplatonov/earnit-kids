package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramParentLinkChallengeEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramSecurityAuditEventEntity;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.repository.TelegramParentLinkChallengeRepository;
import com.sashplatonov.earnit.kids.repository.TelegramSecurityAuditEventRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramAccountConnectionServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-08-14T09:00:00Z");

    @Mock FamilyRepository families;
    @Mock ParentAccountRepository parents;
    @Mock FamilyParentMembershipRepository memberships;
    @Mock TelegramIdentityRepository identities;
    @Mock TelegramParentLinkChallengeRepository challenges;
    @Mock TelegramSecurityAuditEventRepository audits;
    @Mock TelegramInitDataVerifier verifier;
    @Mock TelegramConfig config;
    @Mock TimeProvider timeProvider;
    @Mock TelegramFeatureGate featureGate;

    private TelegramAccountConnectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TelegramAccountConnectionServiceImpl(
            families, parents, memberships, identities, challenges, audits, verifier, config,
            new SecureTokenGenerator(), timeProvider);
        service.featureGate = featureGate;
    }

    @Test
    void startCreatesOpaqueExpiringChallengeForTheAuthenticatedParent() {
        stubNow();
        stubParentContext();
        when(config.botUsername()).thenReturn(Optional.of("earnit_bot"));
        when(identities.findActiveParentByParentAccountId(4)).thenReturn(Optional.empty());

        OperationResult<?> result = service.start("family-1", "parent@example.test");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        var launch = ((OperationResult.Success<com.sashplatonov.earnit.kids.dto.response.TelegramLinkLaunchResponse>) result)
            .value();
        assertThat(launch.launchUrl()).startsWith("https://t.me/earnit_bot?startapp=");
        ArgumentCaptor<TelegramParentLinkChallengeEntity> challenge = ArgumentCaptor.forClass(
            TelegramParentLinkChallengeEntity.class);
        verify(challenges).persist(challenge.capture());
        assertThat(challenge.getValue().getSecretDigest()).doesNotContain("?startapp=");
        assertThat(challenge.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void connectionDoesNotOfferTheMiniAppOutsideTheSelectedFamilysRollout() {
        stubParentContext();
        when(featureGate.isMiniAppEnabled("family-1")).thenReturn(false);
        when(identities.findActiveParentByParentAccountId(4)).thenReturn(Optional.of(
            TelegramIdentityEntity.builder().familyId(1).parentAccountId(4).telegramUserId(77L)
                .role("parent").active(true).linkedAt(NOW).build()));

        OperationResult<com.sashplatonov.earnit.kids.dto.response.TelegramAccountConnectionResponse> result =
            service.connection("family-1", "parent@example.test");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        var response = ((OperationResult.Success<com.sashplatonov.earnit.kids.dto.response.TelegramAccountConnectionResponse>) result)
            .value();
        assertThat(response.telegramConnected()).isTrue();
        assertThat(response.miniAppUrl()).isNull();
    }

    @Test
    void completeLinksVerifiedTelegramIdentityOnlyOnce() {
        stubNow();
        TelegramParentLinkChallengeEntity challenge = TelegramParentLinkChallengeEntity.builder()
            .parentAccountId(4).familyId(1).secretDigest("digest").expiresAt(NOW.plusSeconds(60)).createdAt(NOW).build();
        when(verifier.verify("verified-init-data")).thenReturn(Optional.of(
            new TelegramInitDataVerifier.VerifiedInitData(77L, NOW)));
        when(challenges.findByDigestForUpdate(any())).thenReturn(Optional.of(challenge));
        when(families.findByDbId(1)).thenReturn(Optional.of(family()));
        when(parents.findByIdForUpdate(4)).thenReturn(Optional.of(parent()));
        when(memberships.findByParentAndFamily(4, 1)).thenReturn(Optional.of(membership()));
        when(featureGate.isMiniAppEnabled("family-1")).thenReturn(true);
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.empty());
        when(identities.findActiveParentByParentAccountId(4)).thenReturn(Optional.empty());

        OperationResult<Void> result = service.complete("raw-token", "verified-init-data");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(identities).persist(org.mockito.ArgumentMatchers.<TelegramIdentityEntity>argThat(identity ->
            identity.getParentAccountId().equals(4) && identity.getFamilyId().equals(1)
                && identity.getTelegramUserId().equals(77L)));
        assertThat(challenge.getConsumedAt()).isEqualTo(NOW);
        verify(audits).persist(org.mockito.ArgumentMatchers.<TelegramSecurityAuditEventEntity>any());
    }

    @Test
    void completeRejectsAnIdentityAlreadyLinkedToAnotherAccount() {
        stubNow();
        TelegramParentLinkChallengeEntity challenge = TelegramParentLinkChallengeEntity.builder()
            .parentAccountId(4).familyId(1).secretDigest("digest").expiresAt(NOW.plusSeconds(60)).createdAt(NOW).build();
        when(verifier.verify("verified-init-data")).thenReturn(Optional.of(
            new TelegramInitDataVerifier.VerifiedInitData(77L, NOW)));
        when(challenges.findByDigestForUpdate(any())).thenReturn(Optional.of(challenge));
        when(families.findByDbId(1)).thenReturn(Optional.of(family()));
        when(parents.findByIdForUpdate(4)).thenReturn(Optional.of(parent()));
        when(memberships.findByParentAndFamily(4, 1)).thenReturn(Optional.of(membership()));
        when(featureGate.isMiniAppEnabled("family-1")).thenReturn(true);
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(
            TelegramIdentityEntity.builder().familyId(2).parentAccountId(9).telegramUserId(77L)
                .role("parent").active(true).linkedAt(NOW).build()));

        OperationResult<Void> result = service.complete("raw-token", "verified-init-data");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        verify(identities, never()).persist(org.mockito.ArgumentMatchers.<TelegramIdentityEntity>any());
        assertThat(challenge.getConsumedAt()).isNull();
    }

    @Test
    void completeRejectsAChallengeWhenTheFamilyLeavesTheTelegramRollout() {
        stubNow();
        TelegramParentLinkChallengeEntity challenge = TelegramParentLinkChallengeEntity.builder()
            .parentAccountId(4).familyId(1).secretDigest("digest").expiresAt(NOW.plusSeconds(60)).createdAt(NOW).build();
        when(verifier.verify("verified-init-data")).thenReturn(Optional.of(
            new TelegramInitDataVerifier.VerifiedInitData(77L, NOW)));
        when(challenges.findByDigestForUpdate(any())).thenReturn(Optional.of(challenge));
        when(families.findByDbId(1)).thenReturn(Optional.of(family()));
        when(parents.findByIdForUpdate(4)).thenReturn(Optional.of(parent()));
        when(memberships.findByParentAndFamily(4, 1)).thenReturn(Optional.of(membership()));
        when(featureGate.isMiniAppEnabled("family-1")).thenReturn(false);

        OperationResult<Void> result = service.complete("raw-token", "verified-init-data");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        verify(identities, never()).persist(org.mockito.ArgumentMatchers.<TelegramIdentityEntity>any());
        assertThat(challenge.getConsumedAt()).isNull();
    }

    @Test
    void unlinkOnlyDeactivatesTheCurrentParentsIdentityInTheSelectedFamily() {
        stubNow();
        stubParentContext();
        TelegramIdentityEntity identity = TelegramIdentityEntity.builder()
            .id(8).familyId(1).parentAccountId(4).telegramUserId(77L).role("parent").active(true)
            .linkedAt(NOW).build();
        when(identities.findActiveParentByParentAccountId(4)).thenReturn(Optional.of(identity));

        OperationResult<Void> result = service.unlink("family-1", "parent@example.test");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(identity.isActive()).isFalse();
        assertThat(identity.getUnlinkedAt()).isEqualTo(NOW);
        verify(audits).persist(org.mockito.ArgumentMatchers.<TelegramSecurityAuditEventEntity>any());
    }

    private void stubParentContext() {
        when(families.findById("family-1")).thenReturn(Optional.of(family()));
        when(parents.findByEmail("parent@example.test")).thenReturn(Optional.of(parent()));
        when(memberships.findByParentAndFamily(4, 1)).thenReturn(Optional.of(membership()));
    }

    private void stubNow() {
        when(timeProvider.now()).thenReturn(NOW);
    }

    private FamilyEntity family() {
        return FamilyEntity.builder().id(1).familyId("family-1").email("family@example.test")
            .adminPassword("hash").build();
    }

    private ParentAccountEntity parent() {
        return ParentAccountEntity.builder().id(4).email("parent@example.test").passwordHash("hash").build();
    }

    private FamilyParentMembershipEntity membership() {
        return FamilyParentMembershipEntity.builder().id(3).parentAccountId(4).familyId(1)
            .permission(FamilyParentMembershipEntity.Permission.editor).build();
    }
}
