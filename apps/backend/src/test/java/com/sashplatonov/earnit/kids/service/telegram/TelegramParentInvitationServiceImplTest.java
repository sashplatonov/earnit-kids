package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramParentInvitationEntity;
import com.sashplatonov.earnit.kids.dto.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.repository.TelegramParentInvitationRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramParentInvitationServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-08-14T09:00:00Z");

    @Mock FamilyRepository families;
    @Mock TelegramParentInvitationRepository invitations;
    @Mock ParentAccountRepository parents;
    @Mock FamilyParentMembershipRepository memberships;
    @Mock TelegramIdentityService identityService;
    @Mock TelegramInitDataVerifier verifier;
    @Mock TelegramConfig config;
    @Mock SecureTokenGenerator tokens;
    @Mock TimeProvider timeProvider;

    @InjectMocks TelegramParentInvitationServiceImpl service;

    private TelegramParentInvitationEntity invitation() {
        return TelegramParentInvitationEntity.builder()
            .id(1)
            .familyId(7)
            .secretDigest("digest")
            .expiresAt(NOW.plusSeconds(900))
            .issuedBy("parent@example.test")
            .createdAt(NOW)
            .build();
    }

    @Test
    void invite_createsLaunchUrlWhenBotConfigured() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(1));
        when(config.botUsername()).thenReturn(Optional.of("earnit_bot"));
        when(tokens.generateHexToken(32)).thenReturn("0123456789abcdef0123456789abcdef");

        OperationResult<TelegramLinkLaunchResponse> result = service.invite("family-1", "parent@example.test", NOW);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        var launch = ((OperationResult.Success<TelegramLinkLaunchResponse>) result).value();
        assertThat(launch.launchUrl()).isEqualTo(
            "https://t.me/earnit_bot?startapp=pi_0123456789abcdef0123456789abcdef");
        verify(invitations).persist(any(TelegramParentInvitationEntity.class));
    }

    @Test
    void invite_failsWhenBotNotConfigured() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(1));
        when(config.botUsername()).thenReturn(Optional.empty());

        OperationResult<TelegramLinkLaunchResponse> result = service.invite("family-1", "parent@example.test", NOW);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void accept_bindsNewParentWhenEmailIsFree() {
        when(verifier.verify("signed-data")).thenReturn(Optional.of(
            new TelegramInitDataVerifier.VerifiedInitData(77L, NOW)));
        when(identityService.findActiveByTelegramUserId(77L)).thenReturn(Optional.empty());
        when(invitations.findByDigestForUpdate(anyString())).thenReturn(Optional.of(invitation()));
        when(parents.findByEmail("maria@example.com")).thenReturn(Optional.empty());
        when(identityService.linkParent(eq(7), eq(77L), any(), eq("maria@example.com"), eq(NOW))).thenReturn(
            new TelegramIdentityService.TelegramIdentity(3, 7, null, 77L, "parent"));

        OperationResult<TelegramIdentityService.TelegramIdentity> result =
            service.accept("pi_token", "signed-data", "maria@example.com", NOW);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        // EXPLAIN: persist is called via mock; verify the entity was persisted
        verify(parents).persist(any(ParentAccountEntity.class));
        verify(memberships).persist(any(com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity.class));
        verify(identityService).linkParent(eq(7), eq(77L), any(), eq("maria@example.com"), eq(NOW));
    }

    @Test
    void accept_rejectsWhenTelegramAlreadyLinked() {
        when(verifier.verify("signed-data")).thenReturn(Optional.of(
            new TelegramInitDataVerifier.VerifiedInitData(77L, NOW)));
        when(identityService.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(
            new TelegramIdentityService.TelegramIdentity(5, 7, null, 77L, "parent")));

        OperationResult<TelegramIdentityService.TelegramIdentity> result =
            service.accept("pi_token", "signed-data", "maria@example.com", NOW);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void accept_stripsPrefixBeforeLookup() {
        when(verifier.verify("signed-data")).thenReturn(Optional.of(
            new TelegramInitDataVerifier.VerifiedInitData(77L, NOW)));
        when(identityService.findActiveByTelegramUserId(77L)).thenReturn(Optional.empty());
        when(invitations.findByDigestForUpdate(anyString())).thenReturn(Optional.of(invitation()));
        when(parents.findByEmail("maria@example.com")).thenReturn(Optional.empty());
        when(identityService.linkParent(eq(7), eq(77L), any(), eq("maria@example.com"), eq(NOW))).thenReturn(
            new TelegramIdentityService.TelegramIdentity(3, 7, null, 77L, "parent"));

        OperationResult<TelegramIdentityService.TelegramIdentity> result =
            service.accept("pi_token", "signed-data", "maria@example.com", NOW);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(invitations).findByDigestForUpdate(anyString());
    }

    @Test
    void accept_rejectsWhenPrefixMissing() {
        when(verifier.verify("signed-data")).thenReturn(Optional.of(
            new TelegramInitDataVerifier.VerifiedInitData(77L, NOW)));

        OperationResult<TelegramIdentityService.TelegramIdentity> result =
            service.accept("token", "signed-data", "maria@example.com", NOW);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void accept_rejectsWhenInvitationIsExpired() {
        var expired = invitation();
        expired.setExpiresAt(NOW.minusSeconds(1));
        when(verifier.verify("signed-data")).thenReturn(Optional.of(
            new TelegramInitDataVerifier.VerifiedInitData(77L, NOW)));
        when(identityService.findActiveByTelegramUserId(77L)).thenReturn(Optional.empty());
        when(invitations.findByDigestForUpdate(anyString())).thenReturn(Optional.of(expired));

        OperationResult<TelegramIdentityService.TelegramIdentity> result =
            service.accept("pi_token", "signed-data", "maria@example.com", NOW);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }
}