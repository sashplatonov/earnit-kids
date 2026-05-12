package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.domain.model.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyParentAccessServiceImpl implements FamilyParentAccessService {

    private final ParentAccountRepository parentAccountRepository;
    private final FamilyParentMembershipRepository membershipRepository;

    @Override
    @Transactional
    public OperationResult<List<ParentMembershipDto>> listMemberships(Integer familyId) {
        var memberships = membershipRepository.findByFamilyId(familyId);
        var dtos = memberships.stream()
            .map(m -> {
                var parentOpt = parentAccountRepository.findById(m.getParentAccountId());
                String email = parentOpt.map(ParentAccountEntity::getEmail).orElse("unknown");
                return new ParentMembershipDto(
                    m.getId(),
                    email,
                    m.getPermission().name(),
                    m.getStatus()
                );
            })
            .toList();
        return OperationResult.success(dtos);
    }

    @Override
    @Transactional
    public OperationResult<ParentMembershipDto> addMembership(Integer familyId, String email, String permission, String invitedByEmail) {
        // Check for existing membership
        var existingParent = parentAccountRepository.findByEmail(email);
        if (existingParent.isPresent()) {
            var existingMembership = membershipRepository.findByParentAndFamily(existingParent.get().getId(), familyId);
            if (existingMembership.isPresent()) {
                return OperationResult.failure(BackendMessages.message("parentAccess.alreadyMember"));
            }
        }

        // Get or create parent account
        ParentAccountEntity parent = existingParent.orElseGet(() -> {
            var newParent = new ParentAccountEntity();
            newParent.setEmail(email);
            newParent.setPasswordHash(""); // Empty password - invitation flow
            newParent.setVerified(false);
            parentAccountRepository.persist(newParent);
            return newParent;
        });

        // Validate permission
        FamilyParentMembershipEntity.Permission perm;
        try {
            perm = FamilyParentMembershipEntity.Permission.valueOf(permission);
        } catch (IllegalArgumentException e) {
            return OperationResult.failure(BackendMessages.message("parentAccess.invalidPermission"));
        }

        // Create membership
        var membership = new FamilyParentMembershipEntity();
        membership.setParentAccountId(parent.getId());
        membership.setFamilyId(familyId);
        membership.setPermission(perm);
        membership.setStatus("active");
        membership.setInvitedByEmail(invitedByEmail);
        membership.setInvitedAt(Instant.now());
        membershipRepository.persist(membership);

        log.info("Added parent membership: email={}, familyId={}, permission={}", email, familyId, permission);

        return OperationResult.success(new ParentMembershipDto(
            membership.getId(),
            parent.getEmail(),
            membership.getPermission().name(),
            membership.getStatus()
        ));
    }

    @Override
    @Transactional
    public OperationResult<ParentMembershipDto> updateMembership(Integer membershipId, String permission, Integer familyId) {
        var membershipOpt = membershipRepository.findById(membershipId);
        if (membershipOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("parentAccess.membershipNotFound"));
        }

        var membership = membershipOpt.get();
        if (!membership.getFamilyId().equals(familyId)) {
            return OperationResult.failure(BackendMessages.message("parentAccess.notAuthorized"));
        }

        // Validate permission
        FamilyParentMembershipEntity.Permission perm;
        try {
            perm = FamilyParentMembershipEntity.Permission.valueOf(permission);
        } catch (IllegalArgumentException e) {
            return OperationResult.failure(BackendMessages.message("parentAccess.invalidPermission"));
        }

        // Prevent removing last family_admin
        if (membership.getPermission() == FamilyParentMembershipEntity.Permission.family_admin
            && perm != FamilyParentMembershipEntity.Permission.family_admin) {
            long adminCount = membershipRepository.countFamilyAdmins(familyId);
            if (adminCount <= 1) {
                return OperationResult.failure(BackendMessages.message("parentAccess.cannotRemoveLastAdmin"));
            }
        }

        membership.setPermission(perm);
        membershipRepository.persist(membership);

        var parentOpt = parentAccountRepository.findById(membership.getParentAccountId());
        String email = parentOpt.map(ParentAccountEntity::getEmail).orElse("unknown");

        log.info("Updated parent membership: id={}, permission={}", membershipId, permission);

        return OperationResult.success(new ParentMembershipDto(
            membership.getId(),
            email,
            membership.getPermission().name(),
            membership.getStatus()
        ));
    }

    @Override
    @Transactional
    public OperationResult<Void> removeMembership(Integer membershipId, Integer familyId) {
        var membershipOpt = membershipRepository.findById(membershipId);
        if (membershipOpt.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("parentAccess.membershipNotFound"));
        }

        var membership = membershipOpt.get();
        if (!membership.getFamilyId().equals(familyId)) {
            return OperationResult.failure(BackendMessages.message("parentAccess.notAuthorized"));
        }

        // Prevent removing last family_admin
        if (membership.getPermission() == FamilyParentMembershipEntity.Permission.family_admin) {
            long adminCount = membershipRepository.countFamilyAdmins(familyId);
            if (adminCount <= 1) {
                return OperationResult.failure(BackendMessages.message("parentAccess.cannotRemoveLastAdmin"));
            }
        }

        membershipRepository.delete(membership);

        log.info("Removed parent membership: id={}, familyId={}", membershipId, familyId);

        return OperationResult.success(null);
    }
}