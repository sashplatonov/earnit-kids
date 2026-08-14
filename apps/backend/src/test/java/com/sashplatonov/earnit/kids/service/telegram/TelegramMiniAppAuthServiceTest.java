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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private TelegramMiniAppAuthService service(TelegramInitDataVerifier verifier,
                                                TelegramIdentityRepository identities,
                                                FamilyRepository families,
                                                ChildRepository children,
                                                ParentAccountRepository parents,
                                                FamilyParentMembershipRepository memberships) {
        TelegramMiniAppAuthService service = new TelegramMiniAppAuthService(verifier, identities);
        service.families = families;
        service.children = children;
        service.parents = parents;
        service.memberships = memberships;
        return service;
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
