package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.ChildStatus;
import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.dto.response.ChildTelegramConnectionResponse;
import com.sashplatonov.earnit.kids.dto.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramChildConnectionServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-08-14T09:00:00Z");

    @Mock FamilyRepository families;
    @Mock ChildRepository children;
    @Mock TelegramIdentityRepository identities;
    @Mock TelegramIdentityService identityService;
    @Mock TelegramConfig config;
    @Mock TimeProvider timeProvider;

    @InjectMocks TelegramChildConnectionServiceImpl service;

    private ChildEntity child(int id, int familyDbId) {
        return ChildEntity.builder()
            .id(id)
            .familyDbId(familyDbId)
            .name("Маша")
            .token("token")
            .status(ChildStatus.ACTIVE.name())
            .build();
    }

    @Test
    void connection_reportsUnlinkedWhenNoIdentity() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(1));
        when(children.findByIdOptional(15)).thenReturn(Optional.of(child(15, 1)));
        when(identities.findActiveChild(15)).thenReturn(Optional.empty());

        OperationResult<ChildTelegramConnectionResponse> result = service.connection("family-1", 15);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        var value = ((OperationResult.Success<ChildTelegramConnectionResponse>) result).value();
        assertThat(value.linked()).isFalse();
        assertThat(value.telegramUserId()).isNull();
    }

    @Test
    void connection_reportsLinkedWhenIdentityExists() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(1));
        when(children.findByIdOptional(15)).thenReturn(Optional.of(child(15, 1)));
        TelegramIdentityEntity identity = TelegramIdentityEntity.builder()
            .telegramUserId(42L)
            .role("child")
            .active(true)
            .linkedAt(NOW)
            .build();
        when(identities.findActiveChild(15)).thenReturn(Optional.of(identity));

        OperationResult<ChildTelegramConnectionResponse> result = service.connection("family-1", 15);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        var value = ((OperationResult.Success<ChildTelegramConnectionResponse>) result).value();
        assertThat(value.linked()).isTrue();
        assertThat(value.telegramUserId()).isEqualTo(42L);
    }

    @Test
    void invite_rejectsChildFromAnotherFamily() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(1));
        when(children.findByIdOptional(15)).thenReturn(Optional.of(child(15, 99)));

        OperationResult<?> result = service.invite("family-1", 15);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void invite_createsLaunchUrlWhenBotConfigured() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(1));
        when(children.findByIdOptional(15)).thenReturn(Optional.of(child(15, 1)));
        when(config.botUsername()).thenReturn(Optional.of("earnit_bot"));
        when(identities.findActiveChild(15)).thenReturn(Optional.empty());
        when(timeProvider.now()).thenReturn(NOW);
        when(identityService.issueChildInvitation(eq(1), eq(15), eq("parent"), any(), eq(NOW)))
            .thenReturn(new TelegramIdentityService.TelegramChildInvitationToken("secret-token", 7));

        OperationResult<TelegramLinkLaunchResponse> result = service.invite("family-1", 15);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        var launch = ((OperationResult.Success<TelegramLinkLaunchResponse>) result).value();
        assertThat(launch.launchUrl()).isEqualTo("https://t.me/earnit_bot?startapp=ci_secret-token");
        verify(identityService).issueChildInvitation(eq(1), eq(15), eq("parent"),
            eq(NOW.plusSeconds(900)), eq(NOW));
    }

    @Test
    void invite_failsWhenBotNotConfigured() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(1));
        when(children.findByIdOptional(15)).thenReturn(Optional.of(child(15, 1)));
        when(config.botUsername()).thenReturn(Optional.empty());

        OperationResult<?> result = service.invite("family-1", 15);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
    }

    @Test
    void unlink_unlinksExistingIdentity() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(1));
        when(children.findByIdOptional(15)).thenReturn(Optional.of(child(15, 1)));
        TelegramIdentityEntity identity = TelegramIdentityEntity.builder()
            .telegramUserId(42L)
            .role("child")
            .active(true)
            .linkedAt(NOW)
            .build();
        when(identities.findActiveChild(15)).thenReturn(Optional.of(identity));
        when(timeProvider.now()).thenReturn(NOW);

        OperationResult<Void> result = service.unlink("family-1", 15);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(identityService).unlink(42L, "parent", NOW);
    }

    @Test
    void unlink_succeedsEvenWhenNoIdentityLinked() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(1));
        when(children.findByIdOptional(15)).thenReturn(Optional.of(child(15, 1)));
        when(identities.findActiveChild(15)).thenReturn(Optional.empty());

        OperationResult<Void> result = service.unlink("family-1", 15);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(identityService, org.mockito.Mockito.never()).unlink(anyLong(), any(), any());
    }
}
