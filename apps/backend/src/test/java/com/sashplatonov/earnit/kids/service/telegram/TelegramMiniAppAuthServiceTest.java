package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sashplatonov.earnit.kids.service.telegram.admin.AdminAccessService;
import com.sashplatonov.earnit.kids.service.telegram.TelegramIdentityService;

class TelegramMiniAppAuthServiceTest {
    @Test
    void authenticatesLinkedParent() {
        TelegramInitDataVerifier verifier = mock(TelegramInitDataVerifier.class);
        TelegramIdentityRepository identities = mock(TelegramIdentityRepository.class);
        FamilyRepository families = mock(FamilyRepository.class);
        ChildRepository children = mock(ChildRepository.class);
        ParentAccountRepository parents = mock(ParentAccountRepository.class);
        FamilyParentMembershipRepository memberships = mock(FamilyParentMembershipRepository.class);
        when(verifier.verify("parent-data")).thenReturn(Optional.of(verified(77L)));
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(identity("parent", null)));
        when(families.findByDbId(1)).thenReturn(Optional.of(family()));
        when(parents.findByIdOptional(4)).thenReturn(Optional.of(parent()));
        when(memberships.findByParentAndFamily(4, 1)).thenReturn(Optional.of(membership()));

        var result = service(verifier, identities, families, children, parents, memberships).authenticate("parent-data");

        assertThat(result).isInstanceOf(com.sashplatonov.earnit.kids.util.OperationResult.Success.class);
        var payload = ((com.sashplatonov.earnit.kids.util.OperationResult.Success<com.sashplatonov.earnit.kids.dto.response.AuthPayload>) result).value();
        assertThat(payload.role()).isEqualTo("admin");
        assertThat(payload.familyId()).isEqualTo("family-1");
        assertThat(payload.permission()).isEqualTo("editor");
    }

    @Test
    void authenticatesLinkedChildOnlyWithinItsFamily() {
        TelegramInitDataVerifier verifier = mock(TelegramInitDataVerifier.class);
        TelegramIdentityRepository identities = mock(TelegramIdentityRepository.class);
        FamilyRepository families = mock(FamilyRepository.class);
        ChildRepository children = mock(ChildRepository.class);
        when(verifier.verify("child-data")).thenReturn(Optional.of(verified(88L)));
        when(identities.findActiveByTelegramUserId(88L)).thenReturn(Optional.of(identity("child", 7)));
        when(families.findByDbId(1)).thenReturn(Optional.of(family()));
        when(children.findByIdOptional(7)).thenReturn(Optional.of(ChildEntity.builder().id(7).familyDbId(1).name("Kid").build()));

        var result = service(verifier, identities, families, children, mock(ParentAccountRepository.class),
            mock(FamilyParentMembershipRepository.class)).authenticate("child-data");

        assertThat(result).isInstanceOf(com.sashplatonov.earnit.kids.util.OperationResult.Success.class);
        var payload = ((com.sashplatonov.earnit.kids.util.OperationResult.Success<com.sashplatonov.earnit.kids.dto.response.AuthPayload>) result).value();
        assertThat(payload.role()).isEqualTo("child");
        assertThat(payload.childId()).isEqualTo(7);
        assertThat(payload.childName()).isEqualTo("Kid");
    }

    @Test
    void rejectsIncompleteOrUnsupportedIdentityWithoutIssuingASession() {
        TelegramInitDataVerifier verifier = mock(TelegramInitDataVerifier.class);
        TelegramIdentityRepository identities = mock(TelegramIdentityRepository.class);
        FamilyRepository families = mock(FamilyRepository.class);
        ChildRepository children = mock(ChildRepository.class);
        when(verifier.verify("bad-identity")).thenReturn(Optional.of(verified(99L)));
        when(identities.findActiveByTelegramUserId(99L)).thenReturn(Optional.of(
            TelegramIdentityEntity.builder().familyId(1).telegramUserId(99L).role("unknown").active(true).build()));
        when(families.findByDbId(1)).thenReturn(Optional.of(family()));

        var result = service(verifier, identities, families, children, mock(ParentAccountRepository.class),
            mock(FamilyParentMembershipRepository.class)).authenticate("bad-identity");

        assertThat(result).isInstanceOf(com.sashplatonov.earnit.kids.util.OperationResult.Failure.class);
    }

    @Test
    void acceptsChildInvitationTokenBeforeAuthenticating() {
        TelegramInitDataVerifier verifier = mock(TelegramInitDataVerifier.class);
        TelegramIdentityRepository identities = mock(TelegramIdentityRepository.class);
        TelegramIdentityService identityService = mock(TelegramIdentityService.class);
        FamilyRepository families = mock(FamilyRepository.class);
        ChildRepository children = mock(ChildRepository.class);
        ParentAccountRepository parents = mock(ParentAccountRepository.class);
        FamilyParentMembershipRepository memberships = mock(FamilyParentMembershipRepository.class);
        when(verifier.verify("child-data")).thenReturn(Optional.of(verified(77L)));
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(identity("child", 7)));
        when(families.findByDbId(1)).thenReturn(Optional.of(family()));
        when(children.findByIdOptional(7)).thenReturn(Optional.of(
            ChildEntity.builder().id(7).familyDbId(1).name("Kid").build()));

        TelegramMiniAppAuthService service = new TelegramMiniAppAuthService(
            verifier, identities, identityService, () -> Instant.parse("2026-08-13T12:00:00Z"));
        service.families = families;
        service.children = children;
        service.parents = parents;
        service.memberships = memberships;

        var result = service.authenticate("child-data", "ci_child-secret");

        assertThat(result).isInstanceOf(com.sashplatonov.earnit.kids.util.OperationResult.Success.class);
        var payload = ((com.sashplatonov.earnit.kids.util.OperationResult.Success<com.sashplatonov.earnit.kids.dto.response.AuthPayload>) result).value();
        assertThat(payload.role()).isEqualTo("child");
        verify(identityService).acceptChildInvitation(eq("child-secret"), eq(77L), eq(Instant.parse("2026-08-13T12:00:00Z")));
    }

    private TelegramMiniAppAuthService service(TelegramInitDataVerifier verifier,
                                                TelegramIdentityRepository identities,
                                                FamilyRepository families,
                                                ChildRepository children,
                                                ParentAccountRepository parents,
                                                FamilyParentMembershipRepository memberships) {
        TelegramMiniAppAuthService service = new TelegramMiniAppAuthService(
            verifier, identities, mock(TelegramIdentityService.class), () -> Instant.parse("2026-08-13T12:00:00Z"));
        service.families = families;
        service.children = children;
        service.parents = parents;
        service.memberships = memberships;
        // EXPLAIN: Admin access service mock – will be set per test if needed
        service.adminAccessService = mock(AdminAccessService.class);
        return service;
    }

    @Test
    void authenticatesAdminWhenTelegramIdIsAdmin() {
        // Arrange – set up mocks and a Telegram ID that is considered an admin
        TelegramInitDataVerifier verifier = mock(TelegramInitDataVerifier.class);
        TelegramIdentityRepository identities = mock(TelegramIdentityRepository.class);
        FamilyRepository families = mock(FamilyRepository.class);
        ChildRepository children = mock(ChildRepository.class);
        ParentAccountRepository parents = mock(ParentAccountRepository.class);
        FamilyParentMembershipRepository memberships = mock(FamilyParentMembershipRepository.class);

        // Simulate verification of init data returning a telegram user ID 123L
        when(verifier.verify("admin-data")).thenReturn(Optional.of(verified(123L)));
        // Identity record – role can be anything; admin check occurs before role handling
        // Create an identity with the admin telegramUserId 123L
        TelegramIdentityEntity adminIdentity = TelegramIdentityEntity.builder()
            .familyId(1)
            .parentAccountId(null)
            .childId(null)
            .telegramUserId(123L)
            .role("parent")
            .active(true)
            .build();
        when(identities.findActiveByTelegramUserId(123L)).thenReturn(Optional.of(adminIdentity));
        when(families.findByDbId(1)).thenReturn(Optional.of(family()));

        // Create service instance with mocked admin access service
        TelegramMiniAppAuthService svc = service(verifier, identities, families, children, parents, memberships);
        // Mark the telegram user ID as an admin
        when(svc.adminAccessService.isAdmin(123L)).thenReturn(true);

        var result = svc.authenticate("admin-data");

        assertThat(result).isInstanceOf(com.sashplatonov.earnit.kids.util.OperationResult.Success.class);
            var payload = ((com.sashplatonov.earnit.kids.util.OperationResult.Success<com.sashplatonov.earnit.kids.dto.response.AuthPayload>) result).value();
        assertThat(payload.role()).isEqualTo("admin");
        assertThat(payload.permission()).isEqualTo("family_admin");
        assertThat(payload.familyId()).isEqualTo("family-1");
    }

    private TelegramInitDataVerifier.VerifiedInitData verified(long id) {
        return new TelegramInitDataVerifier.VerifiedInitData(id, Instant.parse("2026-08-13T12:00:00Z"));
    }

    private TelegramIdentityEntity identity(String role, Integer childId) {
        return TelegramIdentityEntity.builder().familyId(1).parentAccountId("parent".equals(role) ? 4 : null)
            .childId(childId).telegramUserId(77L).role(role).active(true).build();
    }

    private ParentAccountEntity parent() {
        return ParentAccountEntity.builder().id(4).email("parent@example.test").passwordHash("hash").build();
    }

    private FamilyParentMembershipEntity membership() {
        return FamilyParentMembershipEntity.builder().parentAccountId(4).familyId(1)
            .permission(FamilyParentMembershipEntity.Permission.editor).build();
    }

    private FamilyEntity family() {
        return FamilyEntity.builder().id(1).familyId("family-1").email("parent@example.test").adminPassword("hash").build();
    }
}
