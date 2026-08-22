package com.sashplatonov.earnit.kids.telegram.api.resource;

import com.sashplatonov.earnit.kids.config.auth.CookieBuilder;
import com.sashplatonov.earnit.kids.family.api.request.ParentInviteAcceptRequest;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramIdentityService;
import com.sashplatonov.earnit.kids.telegram.application.invitation.TelegramParentInvitationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramParentInviteAcceptResourceTest {

    @Test
    void accept_returnsNotFoundWhenTelegramIsDisabled() {
        TelegramFeatureGate gate = mock(TelegramFeatureGate.class);
        when(gate.isEnabled()).thenReturn(false);
        TelegramParentInviteAcceptResource resource = resource(gate, mock(TelegramParentInvitationService.class));
        try (Response response = resource.accept(new ParentInviteAcceptRequest("token", "mail@test", "init"))) {
            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    @Test
    void accept_returnsAuthResponseAndCookiesForValidInvitation() {
        TelegramFeatureGate gate = mock(TelegramFeatureGate.class);
        TelegramParentInvitationService invitations = mock(TelegramParentInvitationService.class);
        CookieBuilder cookies = mock(CookieBuilder.class);
        FamilyRepository families = mock(FamilyRepository.class);
        when(gate.isEnabled()).thenReturn(true);
        when(invitations.accept(any(), any(), any(), any())).thenReturn(OperationResult.success(
            new TelegramIdentityService.TelegramIdentity(7, 3, null, 700L, "admin")));
        when(families.findFamilyIdByDbId(3)).thenReturn(Optional.of("family-3"));
        when(cookies.buildAuthCookies("mail@test", "admin", "family-3", null, "editor"))
            .thenReturn(List.of("app_auth=token"));
        TelegramParentInviteAcceptResource resource = new TelegramParentInviteAcceptResource(
            invitations, gate, cookies, families, () -> Instant.parse("2026-08-20T12:00:00Z"));

        try (Response response = resource.accept(new ParentInviteAcceptRequest("token", "mail@test", "init"))) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeaderString("Set-Cookie")).contains("app_auth=token");
        }
    }

    private static TelegramParentInviteAcceptResource resource(TelegramFeatureGate gate,
                                                                 TelegramParentInvitationService invitations) {
        return new TelegramParentInviteAcceptResource(invitations, gate, mock(CookieBuilder.class),
            mock(FamilyRepository.class), () -> Instant.EPOCH);
    }
}
