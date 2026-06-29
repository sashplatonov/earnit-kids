package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.domain.model.MembershipStatus;
import com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.dto.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FamilyParentAccessServiceImplTest {

    @Mock FamilyRepository familyRepository;
    @Mock ParentAccountRepository parentAccountRepository;
    @Mock FamilyParentMembershipRepository membershipRepository;

    private FamilyParentAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FamilyParentAccessServiceImpl(familyRepository, parentAccountRepository, membershipRepository);
    }

    @Test
    void listMemberships_batchLoadsParentsAndMapsDtos() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity first = membership(11, 7, 1, FamilyParentMembershipEntity.Permission.editor);
        FamilyParentMembershipEntity second = membership(12, 7, 2, FamilyParentMembershipEntity.Permission.viewer);
        ParentAccountEntity parent1 = parent(1, "alice@test.com");
        ParentAccountEntity parent2 = parent(2, "bob@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByFamilyId(7)).thenReturn(List.of(first, second));
        when(parentAccountRepository.findByIdList(List.of(1, 2))).thenReturn(List.of(parent1, parent2));

        OperationResult<List<ParentMembershipDto>> result = service.listMemberships("fam-1");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(((OperationResult.Success<List<ParentMembershipDto>>) result).value())
            .extracting(ParentMembershipDto::email, ParentMembershipDto::permission)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("alice@test.com", FamilyParentMembershipEntity.Permission.editor),
                org.assertj.core.groups.Tuple.tuple("bob@test.com", FamilyParentMembershipEntity.Permission.viewer));
        verify(parentAccountRepository).findByIdList(List.of(1, 2));
    }

    @Test
    void listMemberships_emptyFamily_returnsEmptyList() {
        FamilyEntity family = mockFamily(7);

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByFamilyId(7)).thenReturn(List.of());

        OperationResult<List<ParentMembershipDto>> result = service.listMemberships("fam-1");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(((OperationResult.Success<List<ParentMembershipDto>>) result).value()).isEmpty();
        verify(parentAccountRepository, org.mockito.Mockito.never()).findByIdList(any());
    }

    @Test
    void addMembership_missingFamily_returnsFailure() {
        when(familyRepository.findById("missing")).thenReturn(Optional.empty());

        OperationResult<ParentMembershipDto> result = service.addMembership(
            "missing", "parent@test.com", "editor", "invite@test.com");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("FAMILY_NOT_FOUND");
    }

    @Test
    void addMembership_duplicateMembership_returnsFailure() {
        FamilyEntity family = mockFamily(7);
        ParentAccountEntity parent = parent(1, "parent@test.com");
        FamilyParentMembershipEntity existing = membership(
            10, 7, 1, FamilyParentMembershipEntity.Permission.viewer);

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(parentAccountRepository.findByEmail("parent@test.com")).thenReturn(Optional.of(parent));
        when(membershipRepository.findByParentAndFamily(1, 7)).thenReturn(Optional.of(existing));

        OperationResult<ParentMembershipDto> result = service.addMembership(
            "fam-1", "parent@test.com", "editor", "invite@test.com");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("PARENT_ALREADY_MEMBER");
    }

    @Test
    void addMembership_primaryFamilyAdminEmail_returnsDedicatedFailure() {
        FamilyEntity family = FamilyEntity.builder()
            .id(7)
            .familyId("fam-1")
            .email("owner@test.com")
            .adminPassword("hash")
            .verified(true)
            .build();

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));

        OperationResult<ParentMembershipDto> result = service.addMembership(
            "fam-1", "owner@test.com", "editor", "invite@test.com");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("PARENT_PRIMARY_ADMIN");
    }

    @Test
    void addMembership_invalidPermission_returnsFailure() {
        FamilyEntity family = mockFamily(7);
        ParentAccountEntity parent = parent(1, "parent@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(parentAccountRepository.findByEmail("parent@test.com")).thenReturn(Optional.of(parent));

        OperationResult<ParentMembershipDto> result = service.addMembership(
            "fam-1", "parent@test.com", "invalid", "invite@test.com");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("PARENT_INVALID_PERMISSION");
    }

    @Test
    void addMembership_validInputPersistsParentAndMembership() {
        FamilyEntity family = mockFamily(7);
        ParentAccountEntity parent = parent(1, "parent@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(parentAccountRepository.findByEmail("parent@test.com")).thenReturn(Optional.of(parent));
        doAnswer(invocation -> {
            FamilyParentMembershipEntity entity = invocation.getArgument(0);
            entity.setId(42);
            return null;
        }).when(membershipRepository).persist(any(FamilyParentMembershipEntity.class));

        OperationResult<ParentMembershipDto> result = service.addMembership(
            "fam-1", "parent@test.com", "family_admin", "invite@test.com");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        ParentMembershipDto dto = ((OperationResult.Success<ParentMembershipDto>) result).value();
        assertThat(dto.id()).isEqualTo(42);
        assertThat(dto.email()).isEqualTo("parent@test.com");
        assertThat(dto.permission()).isEqualTo(FamilyParentMembershipEntity.Permission.family_admin);
        assertThat(dto.status()).isEqualTo(MembershipStatus.active);
    }

    @Test
    void updateMembership_lastAdminDemotion_isRejected() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity membership = membership(11, 7, 1, FamilyParentMembershipEntity.Permission.family_admin);

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(membership));
        when(membershipRepository.countFamilyAdmins(7)).thenReturn(1L);

        OperationResult<ParentMembershipDto> result = service.updateMembership(11, "viewer", "fam-1");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("PARENT_LAST_ADMIN");
    }

    @Test
    void updateMembership_validPermission_returnsUpdatedMembership() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity membership = membership(11, 7, 1, FamilyParentMembershipEntity.Permission.viewer);
        ParentAccountEntity parent = parent(1, "parent@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(membership));
        when(parentAccountRepository.findByIdOptional(1)).thenReturn(Optional.of(parent));

        OperationResult<ParentMembershipDto> result = service.updateMembership(11, "editor", "fam-1");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        ParentMembershipDto dto = ((OperationResult.Success<ParentMembershipDto>) result).value();
        assertThat(dto.id()).isEqualTo(11);
        assertThat(dto.email()).isEqualTo("parent@test.com");
        assertThat(dto.permission()).isEqualTo(FamilyParentMembershipEntity.Permission.editor);
        assertThat(membership.getPermission()).isEqualTo(FamilyParentMembershipEntity.Permission.editor);
    }

    @Test
    void removeMembership_nonAdminMembershipDeletesEntry() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity membership = membership(11, 7, 1, FamilyParentMembershipEntity.Permission.editor);

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(membership));

        OperationResult<Void> result = service.removeMembership(11, "fam-1", "admin@test.com");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(membershipRepository).delete(membership);
    }

    @Test
    void removeMembership_otherAdminMembershipIsRejected() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity targetMembership = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.family_admin);
        FamilyParentMembershipEntity actorMembership = membership(12, 7, 1, FamilyParentMembershipEntity.Permission.family_admin);
        ParentAccountEntity actor = parent(1, "admin@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(targetMembership));
        when(parentAccountRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actor));
        when(membershipRepository.findByParentAndFamily(1, 7)).thenReturn(Optional.of(actorMembership));

        OperationResult<Void> result = service.removeMembership(11, "fam-1", "admin@test.com");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<Void>) result).errorCode())
            .isEqualTo("PARENT_ADMIN_DELETE_FORBIDDEN");
        verify(membershipRepository, org.mockito.Mockito.never()).delete(any(FamilyParentMembershipEntity.class));
    }

    private static FamilyEntity mockFamily(int id) {
        FamilyEntity family = org.mockito.Mockito.mock(FamilyEntity.class);
        when(family.getId()).thenReturn(id);
        return family;
    }

    private static ParentAccountEntity parent(int id, String email) {
        return ParentAccountEntity.builder()
            .id(id)
            .email(email)
            .passwordHash("hash")
            .verified(true)
            .build();
    }

    private static FamilyParentMembershipEntity membership(
        int id, int familyId, int parentId, FamilyParentMembershipEntity.Permission permission) {
        return FamilyParentMembershipEntity.builder()
            .id(id)
            .familyId(familyId)
            .parentAccountId(parentId)
            .permission(permission)
            .status(MembershipStatus.active)
            .invitedAt(Instant.parse("2026-05-12T10:00:00Z"))
            .build();
    }
}
