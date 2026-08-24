package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyAdminTransferRequestEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.MembershipStatus;
import com.sashplatonov.earnit.kids.family.application.invitation.ParentInvitationTokenHasher;
import com.sashplatonov.earnit.kids.identity.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.family.api.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyAdminTransferRequestRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.invitation.ParentEmailInvitationRepository;
import com.sashplatonov.earnit.kids.platform.security.SecurityAuditWriter;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramIdentityRepository;
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
    @Mock FamilyAdminTransferRequestRepository transferRequestRepository;
    @Mock TelegramIdentityRepository telegramIdentityRepository;
    @Mock ParentEmailInvitationRepository invitationRepository;
    @Mock SecurityAuditWriter securityAuditWriter;

    private FamilyParentAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FamilyParentAccessServiceImpl(
            familyRepository, parentAccountRepository, membershipRepository, transferRequestRepository,
            telegramIdentityRepository, invitationRepository, securityAuditWriter,
            new ParentInvitationTokenHasher("active", "active-secret", null, null));
    }

    @Test
    void listMemberships_batchLoadsParentsAndMapsDtos() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity first = membership(11, 7, 1, FamilyParentMembershipEntity.Permission.editor);
        FamilyParentMembershipEntity second = membership(12, 7, 2, FamilyParentMembershipEntity.Permission.viewer);
        ParentAccountEntity parent1 = parent(1, "alice@test.com");
        ParentAccountEntity parent2 = parent(2, "bob@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByFamilyIdIncludingInactive(7)).thenReturn(List.of(first, second));
        when(parentAccountRepository.findByIdList(List.of(1, 2))).thenReturn(List.of(parent1, parent2));
        when(telegramIdentityRepository.findActiveParentsByFamilyAndParentAccountIds(7, List.of(1, 2)))
            .thenReturn(List.of());
        when(transferRequestRepository.findPendingByFamily(7)).thenReturn(Optional.empty());

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
    void listMemberships_mapsNamedTelegramParentAndIgnoresInactiveOrOtherFamilyIdentities() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity legacy = membership(11, 7, 1, FamilyParentMembershipEntity.Permission.editor);
        FamilyParentMembershipEntity telegram = membership(12, 7, 2, FamilyParentMembershipEntity.Permission.viewer);
        telegram.setDisplayName("Alex Parent");
        ParentAccountEntity legacyParent = parent(1, "alice@test.com");
        ParentAccountEntity telegramParent = ParentAccountEntity.builder()
            .id(2).email(null).passwordHash("").build();
        TelegramIdentityEntity identity = TelegramIdentityEntity.builder()
            .id(21).familyId(7).parentAccountId(2).telegramUserId(700L)
            .telegramUsername("alex").telegramDisplayName("Alex P").role("parent").active(true).build();

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByFamilyIdIncludingInactive(7)).thenReturn(List.of(legacy, telegram));
        when(parentAccountRepository.findByIdList(List.of(1, 2))).thenReturn(List.of(legacyParent, telegramParent));
        when(telegramIdentityRepository.findActiveParentsByFamilyAndParentAccountIds(7, List.of(1, 2)))
            .thenReturn(List.of(identity));
        when(transferRequestRepository.findPendingByFamily(7)).thenReturn(Optional.empty());

        var result = service.listMemberships("fam-1");

        assertThat(((OperationResult.Success<List<ParentMembershipDto>>) result).value())
            .extracting(ParentMembershipDto::email, ParentMembershipDto::displayName,
                ParentMembershipDto::telegramUserId, ParentMembershipDto::telegramUsername,
                ParentMembershipDto::telegramDisplayName)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("alice@test.com", null, null, null, null),
                org.assertj.core.groups.Tuple.tuple(null, "Alex Parent", 700L, "alex", "Alex P"));
    }

    @Test
    void listMemberships_emptyFamily_returnsEmptyList() {
        FamilyEntity family = mockFamily(7);

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByFamilyIdIncludingInactive(7)).thenReturn(List.of());

        OperationResult<List<ParentMembershipDto>> result = service.listMemberships("fam-1");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(((OperationResult.Success<List<ParentMembershipDto>>) result).value()).isEmpty();
        verify(parentAccountRepository, org.mockito.Mockito.never()).findByIdList(any());
    }

    @Test
    void listMemberships_pendingTransfer_enrichesActorAndTargetRows() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity actor = membership(12, 7, 1, FamilyParentMembershipEntity.Permission.family_admin);
        FamilyParentMembershipEntity target = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.editor);
        ParentAccountEntity actorParent = parent(1, "actor@test.com");
        ParentAccountEntity targetParent = parent(2, "target@test.com");
        FamilyAdminTransferRequestEntity request = request(50, 7, 12, 11);

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByFamilyIdIncludingInactive(7)).thenReturn(List.of(actor, target));
        when(parentAccountRepository.findByIdList(List.of(1, 2))).thenReturn(List.of(actorParent, targetParent));
        when(telegramIdentityRepository.findActiveParentsByFamilyAndParentAccountIds(7, List.of(1, 2)))
            .thenReturn(List.of());
        when(transferRequestRepository.findPendingByFamily(7)).thenReturn(Optional.of(request));

        OperationResult<List<ParentMembershipDto>> result = service.listMemberships("fam-1");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        List<ParentMembershipDto> dtos = ((OperationResult.Success<List<ParentMembershipDto>>) result).value();
        assertThat(dtos).hasSize(2);
        assertThat(dtos).allSatisfy(dto ->
            assertThat(dto.transferRequestStatus()).isEqualTo("pending"));
        assertThat(dtos).extracting(ParentMembershipDto::id).containsExactlyInAnyOrder(12, 11);
        assertThat(dtos.stream().filter(dto -> dto.id().equals(12)).findFirst().orElseThrow()
            .transferRequestActorName()).isEqualTo("actor@test.com");
        assertThat(dtos.stream().filter(dto -> dto.id().equals(11)).findFirst().orElseThrow()
            .transferRequestTargetName()).isEqualTo("target@test.com");
        assertThat(dtos.stream().filter(dto -> dto.id().equals(12)).findFirst().orElseThrow()
            .transferRequestRole()).isEqualTo("actor");
        assertThat(dtos.stream().filter(dto -> dto.id().equals(11)).findFirst().orElseThrow()
            .transferRequestRole()).isEqualTo("target");
        verify(membershipRepository, org.mockito.Mockito.never()).findByIdOptional(any());
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
            com.sashplatonov.earnit.kids.family.domain.model.invitation.ParentEmailInvitationEntity entity = invocation.getArgument(0);
            entity.setId(42);
            return null;
        }).when(invitationRepository).persist(any(com.sashplatonov.earnit.kids.family.domain.model.invitation.ParentEmailInvitationEntity.class));

        OperationResult<ParentMembershipDto> result = service.addMembership(
            "fam-1", "parent@test.com", "family_admin", "invite@test.com");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        ParentMembershipDto dto = ((OperationResult.Success<ParentMembershipDto>) result).value();
        assertThat(dto.id()).isNull();
        assertThat(dto.email()).isEqualTo("parent@test.com");
        assertThat(dto.permission()).isEqualTo(FamilyParentMembershipEntity.Permission.family_admin);
        assertThat(dto.status()).isNull();
        assertThat(dto.invitationStatus()).isEqualTo("pending");
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

        OperationResult<Void> result = service.removeMembership(11, "fam-1", null, "admin@test.com");

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

        OperationResult<Void> result = service.removeMembership(11, "fam-1", null, "admin@test.com");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<Void>) result).errorCode())
            .isEqualTo("PARENT_ADMIN_DELETE_FORBIDDEN");
        verify(membershipRepository, org.mockito.Mockito.never()).delete(any(FamilyParentMembershipEntity.class));
    }

    @Test
    void setMembershipActive_deactivatesNonAdminMembership() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity membership = membership(11, 7, 1, FamilyParentMembershipEntity.Permission.editor);
        ParentAccountEntity parent = parent(1, "parent@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(membership));
        when(parentAccountRepository.findByIdOptional(1)).thenReturn(Optional.of(parent));

        OperationResult<ParentMembershipDto> result = service.setMembershipActive(11, false, "fam-1", null, "admin@test.com");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.inactive);
        ParentMembershipDto dto = ((OperationResult.Success<ParentMembershipDto>) result).value();
        assertThat(dto.status()).isEqualTo(MembershipStatus.inactive);
    }

    @Test
    void setMembershipActive_lastAdminDeactivation_isRejected() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity membership = membership(11, 7, 1, FamilyParentMembershipEntity.Permission.family_admin);

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(membership));
        when(membershipRepository.countFamilyAdmins(7)).thenReturn(1L);

        OperationResult<ParentMembershipDto> result = service.setMembershipActive(11, false, "fam-1", null, "admin@test.com");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("PARENT_LAST_ADMIN");
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.active);
    }

    @Test
    void setMembershipActive_reactivatesMembership() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity membership = membership(11, 7, 1, FamilyParentMembershipEntity.Permission.editor);
        membership.setStatus(MembershipStatus.inactive);
        ParentAccountEntity parent = parent(1, "parent@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(membership));
        when(parentAccountRepository.findByIdOptional(1)).thenReturn(Optional.of(parent));

        OperationResult<ParentMembershipDto> result = service.setMembershipActive(11, true, "fam-1", null, "admin@test.com");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.active);
    }

    @Test
    void createTransferRequest_createsPendingRequestWithoutPermissionChange() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity target = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.editor);
        FamilyParentMembershipEntity actor = membership(12, 7, 1, FamilyParentMembershipEntity.Permission.family_admin);
        ParentAccountEntity actorParent = parent(1, "admin@test.com");
        ParentAccountEntity targetParent = parent(2, "target@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(target));
        when(parentAccountRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actorParent));
        when(membershipRepository.findByParentAndFamily(1, 7)).thenReturn(Optional.of(actor));
        when(transferRequestRepository.findPendingByFamily(7)).thenReturn(Optional.empty());
        when(parentAccountRepository.findByIdOptional(2)).thenReturn(Optional.of(targetParent));

        OperationResult<ParentMembershipDto> result = service.createTransferRequest(11, "fam-1", null, "admin@test.com");

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(target.getPermission()).isEqualTo(FamilyParentMembershipEntity.Permission.editor);
        assertThat(actor.getPermission()).isEqualTo(FamilyParentMembershipEntity.Permission.family_admin);
        ParentMembershipDto dto = ((OperationResult.Success<ParentMembershipDto>) result).value();
        assertThat(dto.transferRequestStatus()).isEqualTo("pending");
        var captor = org.mockito.ArgumentCaptor.forClass(FamilyAdminTransferRequestEntity.class);
        verify(transferRequestRepository).persist(captor.capture());
        assertThat(captor.getValue().getTargetMembershipId()).isEqualTo(11);
    }

    @Test
    void createTransferRequest_includesTelegramIdentityInResponse() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity target = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.editor);
        FamilyParentMembershipEntity actor = membership(12, 7, 1, FamilyParentMembershipEntity.Permission.family_admin);
        ParentAccountEntity actorParent = parent(1, "admin@test.com");
        ParentAccountEntity targetParent = ParentAccountEntity.builder()
            .id(2).email(null).passwordHash("").build();
        TelegramIdentityEntity identity = TelegramIdentityEntity.builder()
            .id(21).familyId(7).parentAccountId(2).telegramUserId(700L)
            .telegramUsername("targettg").telegramDisplayName("Target TG").role("parent").active(true).build();

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(target));
        when(parentAccountRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actorParent));
        when(membershipRepository.findByParentAndFamily(1, 7)).thenReturn(Optional.of(actor));
        when(transferRequestRepository.findPendingByFamily(7)).thenReturn(Optional.empty());
        when(parentAccountRepository.findByIdOptional(2)).thenReturn(Optional.of(targetParent));
        when(telegramIdentityRepository.findActiveParentByParentAccountId(2)).thenReturn(Optional.of(identity));

        ParentMembershipDto dto = ((OperationResult.Success<ParentMembershipDto>) service
            .createTransferRequest(11, "fam-1", null, "admin@test.com")).value();

        assertThat(dto.transferRequestRole()).isEqualTo("target");
        assertThat(dto.telegramUserId()).isEqualTo(700L);
        assertThat(dto.telegramUsername()).isEqualTo("targettg");
        assertThat(dto.telegramDisplayName()).isEqualTo("Target TG");
    }

    @Test
    void createTransferRequest_telegramOnlyAdminUsesAccountIdWithoutEmailLookup() {        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity target = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.editor);
        FamilyParentMembershipEntity actor = membership(12, 7, 1, FamilyParentMembershipEntity.Permission.family_admin);
        ParentAccountEntity targetParent = parent(2, "target@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(target));
        when(membershipRepository.findByParentAndFamily(1, 7)).thenReturn(Optional.of(actor));
        when(transferRequestRepository.findPendingByFamily(7)).thenReturn(Optional.empty());
        when(parentAccountRepository.findByIdOptional(2)).thenReturn(Optional.of(targetParent));

        OperationResult<ParentMembershipDto> result = service.createTransferRequest(11, "fam-1", 1, null);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        verify(parentAccountRepository, org.mockito.Mockito.never()).findByEmail(any());
        var captor = org.mockito.ArgumentCaptor.forClass(FamilyAdminTransferRequestEntity.class);
        verify(transferRequestRepository).persist(captor.capture());
    }

    @Test
    void createTransferRequest_nonAdminActor_isRejected() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity target = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.editor);
        FamilyParentMembershipEntity actor = membership(12, 7, 1, FamilyParentMembershipEntity.Permission.editor);
        ParentAccountEntity actorParent = parent(1, "editor@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(target));
        when(parentAccountRepository.findByEmail("editor@test.com")).thenReturn(Optional.of(actorParent));
        when(membershipRepository.findByParentAndFamily(1, 7)).thenReturn(Optional.of(actor));

        OperationResult<ParentMembershipDto> result = service.createTransferRequest(11, "fam-1", null, "editor@test.com");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("PARENT_ADMIN_DELETE_FORBIDDEN");
    }

    @Test
    void createTransferRequest_secondPendingRequest_returnsConflict() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity target = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.editor);
        FamilyParentMembershipEntity actor = membership(12, 7, 1, FamilyParentMembershipEntity.Permission.family_admin);
        ParentAccountEntity actorParent = parent(1, "admin@test.com");
        FamilyAdminTransferRequestEntity existing = FamilyAdminTransferRequestEntity.builder()
            .id(50).familyId(7).actorMembershipId(12).targetMembershipId(13)
            .status(FamilyAdminTransferRequestEntity.Status.pending).build();

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(target));
        when(parentAccountRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actorParent));
        when(membershipRepository.findByParentAndFamily(1, 7)).thenReturn(Optional.of(actor));
        when(transferRequestRepository.findPendingByFamily(7)).thenReturn(Optional.of(existing));

        OperationResult<ParentMembershipDto> result = service.createTransferRequest(11, "fam-1", null, "admin@test.com");

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("PARENT_TRANSFER_REQUEST_PENDING_EXISTS");
        verify(transferRequestRepository, org.mockito.Mockito.never())
            .persist(any(FamilyAdminTransferRequestEntity.class));
    }
    @Test
    void acceptTransferRequest_promotesTargetAndDemotesActor() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity target = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.editor);
        FamilyParentMembershipEntity actorMembership = membership(12, 7, 1, FamilyParentMembershipEntity.Permission.family_admin);
        FamilyAdminTransferRequestEntity request = request(50, 7, 12, 11);
        ParentAccountEntity targetParent = parent(2, "target@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(transferRequestRepository.findByIdOptional(50)).thenReturn(Optional.of(request));
        when(membershipRepository.findByParentAndFamily(2, 7)).thenReturn(Optional.of(target));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(target));
        when(membershipRepository.findByIdOptional(12)).thenReturn(Optional.of(actorMembership));
        when(transferRequestRepository.findPendingByFamilyAll(7)).thenReturn(List.of(request));
        when(parentAccountRepository.findByIdOptional(2)).thenReturn(Optional.of(targetParent));

        OperationResult<ParentMembershipDto> result = service.acceptTransferRequest(50, "fam-1", 2, null);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(target.getPermission()).isEqualTo(FamilyParentMembershipEntity.Permission.family_admin);
        assertThat(actorMembership.getPermission()).isEqualTo(FamilyParentMembershipEntity.Permission.editor);
        assertThat(request.getStatus()).isEqualTo(FamilyAdminTransferRequestEntity.Status.accepted);
        assertThat(request.getRespondedAt()).isNotNull();
    }

    @Test
    void acceptTransferRequest_wrongCaller_isRejected() {
        FamilyEntity family = mockFamily(7);
        FamilyAdminTransferRequestEntity request = FamilyAdminTransferRequestEntity.builder()
            .id(50).familyId(7).actorMembershipId(12).targetMembershipId(11)
            .status(FamilyAdminTransferRequestEntity.Status.pending).build();
        FamilyParentMembershipEntity nonTarget = membership(13, 7, 3, FamilyParentMembershipEntity.Permission.editor);

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(transferRequestRepository.findByIdOptional(50)).thenReturn(Optional.of(request));
        when(membershipRepository.findByParentAndFamily(3, 7)).thenReturn(Optional.of(nonTarget));

        OperationResult<ParentMembershipDto> result = service.acceptTransferRequest(50, "fam-1", 3, null);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("PARENT_MEMBERSHIP_FORBIDDEN");
    }

    @Test
    void acceptTransferRequest_nonPending_isRejected() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity target = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.editor);
        FamilyAdminTransferRequestEntity request = FamilyAdminTransferRequestEntity.builder()
            .id(50).familyId(7).actorMembershipId(12).targetMembershipId(11)
            .status(FamilyAdminTransferRequestEntity.Status.declined).build();

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(transferRequestRepository.findByIdOptional(50)).thenReturn(Optional.of(request));
        when(membershipRepository.findByParentAndFamily(2, 7)).thenReturn(Optional.of(target));

        OperationResult<ParentMembershipDto> result = service.acceptTransferRequest(50, "fam-1", 2, null);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("PARENT_TRANSFER_REQUEST_NOT_PENDING");
    }

    @Test
    void acceptTransferRequest_cancelsOtherPendingRequests() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity target = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.editor);
        FamilyParentMembershipEntity actorMembership = membership(12, 7, 1, FamilyParentMembershipEntity.Permission.family_admin);
        FamilyAdminTransferRequestEntity request = request(50, 7, 12, 11);
        FamilyAdminTransferRequestEntity other = request(51, 7, 14, 15);
        ParentAccountEntity targetParent = parent(2, "target@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(transferRequestRepository.findByIdOptional(50)).thenReturn(Optional.of(request));
        when(membershipRepository.findByParentAndFamily(2, 7)).thenReturn(Optional.of(target));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(target));
        when(membershipRepository.findByIdOptional(12)).thenReturn(Optional.of(actorMembership));
        when(transferRequestRepository.findPendingByFamilyAll(7)).thenReturn(List.of(request, other));
        when(parentAccountRepository.findByIdOptional(2)).thenReturn(Optional.of(targetParent));

        OperationResult<ParentMembershipDto> result = service.acceptTransferRequest(50, "fam-1", 2, null);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(other.getStatus()).isEqualTo(FamilyAdminTransferRequestEntity.Status.cancelled);
        assertThat(other.getCancelledAt()).isNotNull();
    }

    @Test
    void declineTransferRequest_byTarget_setsDeclined() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity target = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.editor);
        FamilyAdminTransferRequestEntity request = request(50, 7, 12, 11);
        ParentAccountEntity targetParent = parent(2, "target@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(transferRequestRepository.findByIdOptional(50)).thenReturn(Optional.of(request));
        when(membershipRepository.findByParentAndFamily(2, 7)).thenReturn(Optional.of(target));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(target));
        when(parentAccountRepository.findByIdOptional(2)).thenReturn(Optional.of(targetParent));

        OperationResult<ParentMembershipDto> result = service.declineTransferRequest(50, "fam-1", 2, null);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(request.getStatus()).isEqualTo(FamilyAdminTransferRequestEntity.Status.declined);
        assertThat(request.getRespondedAt()).isNotNull();
        assertThat(target.getPermission()).isEqualTo(FamilyParentMembershipEntity.Permission.editor);
    }

    @Test
    void declineTransferRequest_wrongCaller_isRejected() {
        FamilyEntity family = mockFamily(7);
        FamilyAdminTransferRequestEntity request = request(50, 7, 12, 11);
        FamilyParentMembershipEntity nonTarget = membership(13, 7, 3, FamilyParentMembershipEntity.Permission.editor);

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(transferRequestRepository.findByIdOptional(50)).thenReturn(Optional.of(request));
        when(membershipRepository.findByParentAndFamily(3, 7)).thenReturn(Optional.of(nonTarget));

        OperationResult<ParentMembershipDto> result = service.declineTransferRequest(50, "fam-1", 3, null);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("PARENT_MEMBERSHIP_FORBIDDEN");
    }

    @Test
    void cancelTransferRequest_byActor_setsCancelled() {
        FamilyEntity family = mockFamily(7);
        FamilyParentMembershipEntity actor = membership(12, 7, 1, FamilyParentMembershipEntity.Permission.family_admin);
        FamilyParentMembershipEntity target = membership(11, 7, 2, FamilyParentMembershipEntity.Permission.editor);
        FamilyAdminTransferRequestEntity request = request(50, 7, 12, 11);
        ParentAccountEntity targetParent = parent(2, "target@test.com");

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(transferRequestRepository.findByIdOptional(50)).thenReturn(Optional.of(request));
        when(membershipRepository.findByParentAndFamily(1, 7)).thenReturn(Optional.of(actor));
        when(membershipRepository.findByIdOptional(11)).thenReturn(Optional.of(target));
        when(parentAccountRepository.findByIdOptional(2)).thenReturn(Optional.of(targetParent));

        OperationResult<ParentMembershipDto> result = service.cancelTransferRequest(50, "fam-1", 1, null);

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(request.getStatus()).isEqualTo(FamilyAdminTransferRequestEntity.Status.cancelled);
        assertThat(request.getCancelledAt()).isNotNull();
        assertThat(actor.getPermission()).isEqualTo(FamilyParentMembershipEntity.Permission.family_admin);
    }

    @Test
    void cancelTransferRequest_wrongCaller_isRejected() {
        FamilyEntity family = mockFamily(7);
        FamilyAdminTransferRequestEntity request = request(50, 7, 12, 11);
        FamilyParentMembershipEntity nonActor = membership(13, 7, 3, FamilyParentMembershipEntity.Permission.editor);

        when(familyRepository.findById("fam-1")).thenReturn(Optional.of(family));
        when(transferRequestRepository.findByIdOptional(50)).thenReturn(Optional.of(request));
        when(membershipRepository.findByParentAndFamily(3, 7)).thenReturn(Optional.of(nonActor));

        OperationResult<ParentMembershipDto> result = service.cancelTransferRequest(50, "fam-1", 3, null);

        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<ParentMembershipDto>) result).errorCode())
            .isEqualTo("PARENT_MEMBERSHIP_FORBIDDEN");
    }

    private static FamilyAdminTransferRequestEntity request(int id, int familyId, int actorId, int targetId) {
        return FamilyAdminTransferRequestEntity.builder()
            .id(id).familyId(familyId).actorMembershipId(actorId).targetMembershipId(targetId)
            .status(FamilyAdminTransferRequestEntity.Status.pending).build();
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
