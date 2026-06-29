package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.MembershipStatus;
import com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyParentAccessServiceImpl implements FamilyParentAccessService {

    private static final String ERROR_FAMILY_NOT_FOUND = "FAMILY_NOT_FOUND";
    private static final String ERROR_ALREADY_MEMBER = "PARENT_ALREADY_MEMBER";
    private static final String ERROR_PRIMARY_ADMIN = "PARENT_PRIMARY_ADMIN";
    private static final String ERROR_INVALID_PERMISSION = "PARENT_INVALID_PERMISSION";
    private static final String ERROR_MEMBERSHIP_NOT_FOUND = "PARENT_MEMBERSHIP_NOT_FOUND";
    private static final String ERROR_NOT_AUTHORIZED = "PARENT_MEMBERSHIP_FORBIDDEN";
    private static final String ERROR_LAST_ADMIN = "PARENT_LAST_ADMIN";
    private static final String ERROR_ADMIN_DELETE_FORBIDDEN = "PARENT_ADMIN_DELETE_FORBIDDEN";

    private final FamilyRepository familyRepository;
    private final ParentAccountRepository parentAccountRepository;
    private final FamilyParentMembershipRepository membershipRepository;

    @Override
    @Transactional
    public OperationResult<List<ParentMembershipDto>> listMemberships(String familyId) {
        var familyOpt = resolveFamily(familyId);
        if (familyOpt.isEmpty()) {
            return failure(ERROR_FAMILY_NOT_FOUND, "family.familyNotFound");
        }

        var memberships = membershipRepository.findByFamilyId(familyOpt.get().getId());
        if (memberships.isEmpty()) {
            return OperationResult.success(List.of());
        }

        var parentIds = memberships.stream()
            .map(FamilyParentMembershipEntity::getParentAccountId)
            .distinct()
            .toList();
        Map<Integer, ParentAccountEntity> parentsById = parentAccountRepository.findByIdList(parentIds).stream()
            .collect(java.util.stream.Collectors.toMap(
                ParentAccountEntity::getId,
                parent -> parent,
                (left, right) -> left,
                LinkedHashMap::new));
        var dtos = memberships.stream()
            .map(m -> toDto(m, parentsById.get(m.getParentAccountId())))
            .toList();
        return OperationResult.success(dtos);
    }

    @Override
    @Transactional
    public OperationResult<ParentMembershipDto> addMembership(
        String familyId, String email, String permission, String invitedByEmail) {
        var familyOpt = resolveFamily(familyId);
        if (familyOpt.isEmpty()) {
            return failure(ERROR_FAMILY_NOT_FOUND, "family.familyNotFound");
        }

        var family = familyOpt.get();
        Integer familyDbId = family.getId();
        if (family.getEmail() != null && family.getEmail().equalsIgnoreCase(email)) {
            return failure(ERROR_PRIMARY_ADMIN, "parentAccess.primaryAdminAlreadyHasAccess");
        }

        var existingParent = parentAccountRepository.findByEmail(email);
        if (existingParent.isPresent()) {
            var existingMembership = membershipRepository.findByParentAndFamily(
                existingParent.get().getId(), familyDbId);
            if (existingMembership.isPresent()) {
                return failure(ERROR_ALREADY_MEMBER, "parentAccess.alreadyMember");
            }
        }

        ParentAccountEntity parent = existingParent.orElseGet(() -> {
            var newParent = ParentAccountEntity.builder()
                .email(email)
                .passwordHash("")
                .verified(false)
                .build();
            parentAccountRepository.persist(newParent);
            return newParent;
        });

        var permOpt = parsePermission(permission);
        if (permOpt.isEmpty()) {
            return failure(ERROR_INVALID_PERMISSION, "parentAccess.invalidPermission");
        }

        var membership = FamilyParentMembershipEntity.builder()
            .parentAccountId(parent.getId())
            .familyId(familyDbId)
            .permission(permOpt.get())
            .status(MembershipStatus.active)
            .invitedByEmail(invitedByEmail)
            .invitedAt(Instant.now())
            .build();
        membershipRepository.persist(membership);

        log.info("Added parent membership: email={}, familyId={}, permission={}", email, familyId, permission);

        return OperationResult.success(toDto(membership, parent));
    }

    @Override
    @Transactional
    public OperationResult<ParentMembershipDto> updateMembership(
        Integer membershipId, String permission, String familyId) {
        var familyOpt = resolveFamily(familyId);
        if (familyOpt.isEmpty()) {
            return failure(ERROR_FAMILY_NOT_FOUND, "family.familyNotFound");
        }

        var membershipOpt = membershipRepository.findByIdOptional(membershipId);
        if (membershipOpt.isEmpty()) {
            return failure(ERROR_MEMBERSHIP_NOT_FOUND, "parentAccess.membershipNotFound");
        }

        var membership = membershipOpt.get();
        Integer familyDbId = familyOpt.get().getId();
        if (!membership.getFamilyId().equals(familyDbId)) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }

        var permOpt = parsePermission(permission);
        if (permOpt.isEmpty()) {
            return failure(ERROR_INVALID_PERMISSION, "parentAccess.invalidPermission");
        }

        if (membership.getPermission() == FamilyParentMembershipEntity.Permission.family_admin
            && permOpt.get() != FamilyParentMembershipEntity.Permission.family_admin) {
            long adminCount = membershipRepository.countFamilyAdmins(familyDbId);
            if (adminCount <= 1) {
                return failure(ERROR_LAST_ADMIN, "parentAccess.cannotRemoveLastAdmin");
            }
        }

        membership.setPermission(permOpt.get());

        var parent = parentAccountRepository.findByIdOptional(membership.getParentAccountId()).orElse(null);

        log.info("Updated parent membership: id={}, permission={}", membershipId, permission);

        return OperationResult.success(toDto(membership, parent));
    }

    @Override
    @Transactional
    public OperationResult<Void> removeMembership(Integer membershipId, String familyId, String actorEmail) {
        var familyOpt = resolveFamily(familyId);
        if (familyOpt.isEmpty()) {
            return failure(ERROR_FAMILY_NOT_FOUND, "family.familyNotFound");
        }

        var membershipOpt = membershipRepository.findByIdOptional(membershipId);
        if (membershipOpt.isEmpty()) {
            return failure(ERROR_MEMBERSHIP_NOT_FOUND, "parentAccess.membershipNotFound");
        }

        var membership = membershipOpt.get();
        Integer familyDbId = familyOpt.get().getId();
        if (!membership.getFamilyId().equals(familyDbId)) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }

        if (membership.getPermission() == FamilyParentMembershipEntity.Permission.family_admin) {
            if (isDifferentAdmin(membership, familyDbId, actorEmail)) {
                return failure(ERROR_ADMIN_DELETE_FORBIDDEN, "parentAccess.cannotRemoveAdmin");
            }
            long adminCount = membershipRepository.countFamilyAdmins(familyDbId);
            if (adminCount <= 1) {
                return failure(ERROR_LAST_ADMIN, "parentAccess.cannotRemoveLastAdmin");
            }
        }

        membershipRepository.delete(membership);

        log.info("Removed parent membership: id={}, familyId={}", membershipId, familyId);

        return OperationResult.success(null);
    }

    private boolean isDifferentAdmin(
        FamilyParentMembershipEntity membership, Integer familyDbId, String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) {
            return true;
        }

        var actorParentOpt = parentAccountRepository.findByEmail(actorEmail);
        if (actorParentOpt.isEmpty()) {
            return true;
        }

        var actorMembershipOpt = membershipRepository.findByParentAndFamily(actorParentOpt.get().getId(), familyDbId);
        if (actorMembershipOpt.isEmpty()) {
            return true;
        }

        return !membership.getParentAccountId().equals(actorMembershipOpt.get().getParentAccountId());
    }

    private Optional<FamilyEntity> resolveFamily(String familyId) {
        if (familyId == null || familyId.isBlank()) {
            return Optional.empty();
        }
        return familyRepository.findById(familyId);
    }

    private Optional<FamilyParentMembershipEntity.Permission> parsePermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(FamilyParentMembershipEntity.Permission.valueOf(permission));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private <T> OperationResult<T> failure(String errorCode, String messageKey) {
        return OperationResult.failure(errorCode, BackendMessages.message(messageKey));
    }

    private ParentMembershipDto toDto(FamilyParentMembershipEntity membership, ParentAccountEntity parent) {
        String email = parent != null ? parent.getEmail() : "unknown";
        return new ParentMembershipDto(
            membership.getId(),
            email,
            membership.getPermission(),
            membership.getStatus()
        );
    }
}
