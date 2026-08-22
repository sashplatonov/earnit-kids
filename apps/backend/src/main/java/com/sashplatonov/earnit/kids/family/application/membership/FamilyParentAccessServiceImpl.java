package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.family.api.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.MembershipStatus;
import com.sashplatonov.earnit.kids.identity.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramIdentityRepository;
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
    private final TelegramIdentityRepository telegramIdentityRepository;

    @Override
    @Transactional
    public OperationResult<List<ParentMembershipDto>> listMemberships(String familyId) {
        var familyOpt = resolveFamily(familyId);
        if (familyOpt.isEmpty()) {
            return failure(ERROR_FAMILY_NOT_FOUND, "family.familyNotFound");
        }

        var memberships = membershipRepository.findByFamilyIdIncludingInactive(familyOpt.get().getId());
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
        Map<Integer, TelegramIdentityEntity> identitiesByParentId = telegramIdentityRepository
            .findActiveParentsByFamilyAndParentAccountIds(familyOpt.get().getId(), parentIds).stream()
            .collect(java.util.stream.Collectors.toMap(
                TelegramIdentityEntity::getParentAccountId,
                identity -> identity,
                (left, right) -> left,
                LinkedHashMap::new));
        var dtos = memberships.stream()
            .map(m -> toDto(m, parentsById.get(m.getParentAccountId()), identitiesByParentId.get(m.getParentAccountId())))
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

        return OperationResult.success(toDto(membership, parent, null));
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

        return OperationResult.success(toDto(membership, parent, null));
    }

    @Override
    @Transactional
    public OperationResult<Void> removeMembership(
        Integer membershipId, String familyId, Integer actorParentAccountId, String actorEmail) {
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
            if (isDifferentAdmin(membership, familyDbId, actorParentAccountId, actorEmail)) {
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
        FamilyParentMembershipEntity membership,
        Integer familyDbId,
        Integer actorParentAccountId,
        String actorEmail) {
        var actorMembershipOpt = FamilyParentActorResolver.resolve(
            familyDbId, actorParentAccountId, actorEmail, parentAccountRepository, membershipRepository);
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

    private ParentMembershipDto toDto(
        FamilyParentMembershipEntity membership,
        ParentAccountEntity parent,
        TelegramIdentityEntity identity) {
        String email = parent != null ? parent.getEmail() : null;
        return new ParentMembershipDto(
            membership.getId(),
            email,
            membership.getDisplayName(),
            Optional.ofNullable(identity).map(TelegramIdentityEntity::getTelegramUserId).orElse(null),
            Optional.ofNullable(identity).map(TelegramIdentityEntity::getTelegramUsername).orElse(null),
            Optional.ofNullable(identity).map(TelegramIdentityEntity::getTelegramDisplayName).orElse(null),
            membership.getPermission(),
            membership.getStatus()
        );
    }

    @Override
    @Transactional
    public OperationResult<ParentMembershipDto> setMembershipActive(
        Integer membershipId, boolean active, String familyId,
        Integer actorParentAccountId, String actorEmail) {
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

        if (!active && membership.getPermission() == FamilyParentMembershipEntity.Permission.family_admin) {
            long adminCount = membershipRepository.countFamilyAdmins(familyDbId);
            if (adminCount <= 1) {
                return failure(ERROR_LAST_ADMIN, "parentAccess.cannotRemoveLastAdmin");
            }
        }

        membership.setStatus(active ? MembershipStatus.active : MembershipStatus.inactive);

        var parent = parentAccountRepository.findByIdOptional(membership.getParentAccountId()).orElse(null);

        log.info("Set parent membership active={}: id={}, familyId={}", active, membershipId, familyId);

        return OperationResult.success(toDto(membership, parent, null));
    }

    @Override
    @Transactional
    public OperationResult<ParentMembershipDto> transferAdmin(
        Integer membershipId, String familyId, Integer actorParentAccountId, String actorEmail) {
        var familyOpt = resolveFamily(familyId);
        if (familyOpt.isEmpty()) {
            return failure(ERROR_FAMILY_NOT_FOUND, "family.familyNotFound");
        }

        var membershipOpt = membershipRepository.findByIdOptional(membershipId);
        if (membershipOpt.isEmpty()) {
            return failure(ERROR_MEMBERSHIP_NOT_FOUND, "parentAccess.membershipNotFound");
        }

        var target = membershipOpt.get();
        Integer familyDbId = familyOpt.get().getId();
        if (!target.getFamilyId().equals(familyDbId)) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }

        if (target.getStatus() != MembershipStatus.active) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }
        if (target.getPermission() == FamilyParentMembershipEntity.Permission.family_admin) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }

        var actorMembershipOpt = FamilyParentActorResolver.resolve(
            familyDbId, actorParentAccountId, actorEmail, parentAccountRepository, membershipRepository);
        if (actorMembershipOpt.isEmpty()) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }
        var actorMembership = actorMembershipOpt.get();
        if (actorMembership.getPermission() != FamilyParentMembershipEntity.Permission.family_admin) {
            return failure(ERROR_ADMIN_DELETE_FORBIDDEN, "parentAccess.cannotRemoveAdmin");
        }

        actorMembership.setPermission(FamilyParentMembershipEntity.Permission.editor);
        target.setPermission(FamilyParentMembershipEntity.Permission.family_admin);

        var parent = parentAccountRepository.findByIdOptional(target.getParentAccountId()).orElse(null);

        log.info("Transferred family admin: targetMembershipId={}, familyId={}", membershipId, familyId);

        return OperationResult.success(toDto(target, parent, null));
    }
}
