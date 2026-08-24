package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.family.api.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyAdminTransferRequestEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.family.application.invitation.ParentInvitationTokenHasher;
import com.sashplatonov.earnit.kids.family.domain.model.invitation.ParentEmailInvitationEntity;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.MembershipStatus;
import com.sashplatonov.earnit.kids.identity.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyAdminTransferRequestRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.invitation.ParentEmailInvitationRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.platform.security.SecurityAuditWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final String ERROR_INVALID_EMAIL = "PARENT_INVALID_EMAIL";
    private static final String ERROR_PENDING_INVITATION = "PARENT_INVITATION_EXISTS";
    private static final String ERROR_TRANSFER_NOT_FOUND = "PARENT_TRANSFER_REQUEST_NOT_FOUND";
    private static final String ERROR_TRANSFER_NOT_PENDING = "PARENT_TRANSFER_REQUEST_NOT_PENDING";
    private static final String ERROR_TRANSFER_PENDING_EXISTS = "PARENT_TRANSFER_REQUEST_PENDING_EXISTS";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FamilyRepository familyRepository;
    private final ParentAccountRepository parentAccountRepository;
    private final FamilyParentMembershipRepository membershipRepository;
    private final FamilyAdminTransferRequestRepository transferRequestRepository;
    private final TelegramIdentityRepository telegramIdentityRepository;
    private final ParentEmailInvitationRepository invitationRepository;
    private final SecurityAuditWriter securityAuditWriter;
    private final ParentInvitationTokenHasher tokenHasher;

    @Override
    @Transactional
    public OperationResult<List<ParentMembershipDto>> listMemberships(String familyId) {
        var familyOpt = resolveFamily(familyId);
        if (familyOpt.isEmpty()) {
            return failure(ERROR_FAMILY_NOT_FOUND, "family.familyNotFound");
        }

        var memberships = membershipRepository.findByFamilyIdIncludingInactive(familyOpt.get().getId());
        if (memberships.isEmpty()) {
            var invitations = invitationRepository.findPendingByFamily(familyOpt.get().getId()).stream()
                .map(this::toInvitationDto)
                .toList();
            return OperationResult.success(invitations);
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
        Map<Integer, FamilyParentMembershipEntity> membershipsById = memberships.stream()
            .collect(java.util.stream.Collectors.toMap(
                FamilyParentMembershipEntity::getId,
                m -> m,
                (left, right) -> left,
                LinkedHashMap::new));
        var dtos = memberships.stream()
            .map(m -> toDto(m, parentsById.get(m.getParentAccountId()),
                identitiesByParentId.get(m.getParentAccountId())))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        transferRequestRepository.findPendingByFamily(familyOpt.get().getId()).ifPresent(request ->
            enrichWithPendingTransferRequest(dtos, request, membershipsById, parentsById));
        invitationRepository.findPendingByFamily(familyOpt.get().getId()).stream()
            .map(this::toInvitationDto)
            .forEach(dtos::add);
        return OperationResult.success(dtos);
    }

    private void enrichWithPendingTransferRequest(
        List<ParentMembershipDto> dtos,
        FamilyAdminTransferRequestEntity request,
        Map<Integer, FamilyParentMembershipEntity> membershipsById,
        Map<Integer, ParentAccountEntity> parentsById) {
        String actorName = membershipName(request.getActorMembershipId(), membershipsById, parentsById);
        String targetName = membershipName(request.getTargetMembershipId(), membershipsById, parentsById);
        for (int i = 0; i < dtos.size(); i++) {
            ParentMembershipDto dto = dtos.get(i);
            if (dto.id() != null && (dto.id().equals(request.getActorMembershipId())
                || dto.id().equals(request.getTargetMembershipId()))) {
                String role = dto.id().equals(request.getActorMembershipId()) ? "actor" : "target";
                dtos.set(i, new ParentMembershipDto(
                    dto.id(), dto.email(), dto.displayName(), dto.telegramUserId(),
                    dto.telegramUsername(), dto.telegramDisplayName(), dto.permission(), dto.status(),
                    dto.invitationStatus(), "pending", actorName, targetName, request.getId(), role));
            }
        }
    }

    private String membershipName(
        Integer membershipId,
        Map<Integer, FamilyParentMembershipEntity> membershipsById,
        Map<Integer, ParentAccountEntity> parentsById) {
        FamilyParentMembershipEntity membership = membershipsById.get(membershipId);
        if (membership == null) {
            return null;
        }
        if (membership.getDisplayName() != null && !membership.getDisplayName().isBlank()) {
            return membership.getDisplayName();
        }
        return Optional.ofNullable(parentsById.get(membership.getParentAccountId()))
            .map(ParentAccountEntity::getEmail)
            .orElse(null);
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
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return failure(ERROR_INVALID_EMAIL, "parentAccess.invalidEmail");
        }
        if (family.getEmail() != null && family.getEmail().equalsIgnoreCase(normalizedEmail)) {
            securityAuditWriter.write(familyDbId, null, invitedByEmail, normalizedEmail,
                "parent_invitation_rejected", ERROR_PRIMARY_ADMIN);
            return failure(ERROR_PRIMARY_ADMIN, "parentAccess.primaryAdminAlreadyHasAccess");
        }

        var existingParent = parentAccountRepository.findByEmail(normalizedEmail);
        if (existingParent.isPresent()) {
            var existingMembership = membershipRepository.findByParentAndFamily(
                existingParent.get().getId(), familyDbId);
            if (existingMembership.isPresent()) {
                return failure(ERROR_ALREADY_MEMBER, "parentAccess.alreadyMember");
            }
        }

        var permOpt = parsePermission(permission);
        if (permOpt.isEmpty()) {
            return failure(ERROR_INVALID_PERMISSION, "parentAccess.invalidPermission");
        }

        if (invitationRepository.findPending(familyDbId, normalizedEmail).isPresent()) {
            securityAuditWriter.write(familyDbId, null, invitedByEmail, normalizedEmail,
                "parent_invitation_rejected", ERROR_PENDING_INVITATION);
            return failure(ERROR_PENDING_INVITATION, "parentAccess.invitationAlreadyExists");
        }

        byte[] rawToken = new byte[32];
        RANDOM.nextBytes(rawToken);
        String rawTokenValue = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken);
        var invitation = ParentEmailInvitationEntity.builder()
            .familyId(familyDbId)
            .normalizedEmail(normalizedEmail)
            .permission(permOpt.get())
            .invitedByEmail(invitedByEmail)
            .tokenDigest(tokenHasher.digest(rawTokenValue, tokenHasher.activeKeyId()))
            .tokenDigestKeyId(tokenHasher.activeKeyId())
            .expiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60))
            .build();
        invitationRepository.persist(invitation);

        securityAuditWriter.write(familyDbId, null, invitedByEmail, normalizedEmail,
            "parent_invitation_created", "CREATED");
        log.info("Created parent invitation: familyId={}, permission={}", familyId, permission);

        return OperationResult.success(new ParentMembershipDto(null, normalizedEmail, null, null, null, null,
            permOpt.get(), null, ParentEmailInvitationEntity.Status.pending.name()));
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

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= 320 && normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
            ? normalized : null;
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

    private ParentMembershipDto toTransferDto(
        FamilyParentMembershipEntity membership,
        ParentAccountEntity parent,
        TelegramIdentityEntity identity,
        String transferStatus,
        String actorName,
        String targetName,
        Integer transferRequestId) {
        return new ParentMembershipDto(
            membership.getId(),
            parent != null ? parent.getEmail() : null,
            membership.getDisplayName(),
            Optional.ofNullable(identity).map(TelegramIdentityEntity::getTelegramUserId).orElse(null),
            Optional.ofNullable(identity).map(TelegramIdentityEntity::getTelegramUsername).orElse(null),
            Optional.ofNullable(identity).map(TelegramIdentityEntity::getTelegramDisplayName).orElse(null),
            membership.getPermission(),
            membership.getStatus(),
            null,
            transferStatus,
            actorName,
            targetName,
            transferRequestId,
            "target"
        );
    }

    private ParentMembershipDto toInvitationDto(ParentEmailInvitationEntity invitation) {
        return new ParentMembershipDto(null, invitation.getNormalizedEmail(), null, null, null, null,
            invitation.getPermission(), null, invitation.getStatus().name());
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
        // EXPLAIN: transferAdmin now creates a pending approval-based transfer request instead of promoting instantly.
        return createTransferRequest(membershipId, familyId, actorParentAccountId, actorEmail);
    }

    @Override
    @Transactional
    public OperationResult<ParentMembershipDto> createTransferRequest(
        Integer targetMembershipId, String familyId, Integer actorParentAccountId, String actorEmail) {
        var familyOpt = resolveFamily(familyId);
        if (familyOpt.isEmpty()) {
            return failure(ERROR_FAMILY_NOT_FOUND, "family.familyNotFound");
        }

        var membershipOpt = membershipRepository.findByIdOptional(targetMembershipId);
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

        if (transferRequestRepository.findPendingByFamily(familyDbId).isPresent()) {
            return failure(ERROR_TRANSFER_PENDING_EXISTS, "parentAccess.transferRequestPendingExists");
        }

        var request = FamilyAdminTransferRequestEntity.builder()
            .familyId(familyDbId)
            .actorMembershipId(actorMembership.getId())
            .targetMembershipId(target.getId())
            .status(FamilyAdminTransferRequestEntity.Status.pending)
            .build();
        transferRequestRepository.persist(request);

        var parent = parentAccountRepository.findByIdOptional(target.getParentAccountId()).orElse(null);
        var identity = telegramIdentityRepository.findActiveParentByParentAccountId(target.getParentAccountId())
            .orElse(null);

        log.info("Created admin transfer request: targetMembershipId={}, familyId={}",
            targetMembershipId, familyId);

        return OperationResult.success(toTransferDto(
            target, parent, identity, "pending",
            membershipName(actorMembership, parentAccountRepository),
            membershipName(target, parentAccountRepository), request.getId()));
    }

    @Override
    @Transactional
    public OperationResult<ParentMembershipDto> acceptTransferRequest(
        Integer requestId, String familyId, Integer actorParentAccountId, String actorEmail) {
        var familyOpt = resolveFamily(familyId);
        if (familyOpt.isEmpty()) {
            return failure(ERROR_FAMILY_NOT_FOUND, "family.familyNotFound");
        }

        var requestOpt = transferRequestRepository.findByIdOptional(requestId);
        if (requestOpt.isEmpty()) {
            return failure(ERROR_TRANSFER_NOT_FOUND, "parentAccess.transferRequestNotFound");
        }
        var request = requestOpt.get();
        Integer familyDbId = familyOpt.get().getId();
        if (!request.getFamilyId().equals(familyDbId)) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }

        var actorMembershipOpt = FamilyParentActorResolver.resolve(
            familyDbId, actorParentAccountId, actorEmail, parentAccountRepository, membershipRepository);
        if (actorMembershipOpt.isEmpty()) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }
        var actorMembership = actorMembershipOpt.get();
        if (!actorMembership.getId().equals(request.getTargetMembershipId())) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }

        if (request.getStatus() != FamilyAdminTransferRequestEntity.Status.pending) {
            return failure(ERROR_TRANSFER_NOT_PENDING, "parentAccess.transferRequestNotPending");
        }

        var targetOpt = membershipRepository.findByIdOptional(request.getTargetMembershipId());
        var actorRequestMembershipOpt = membershipRepository.findByIdOptional(request.getActorMembershipId());
        if (targetOpt.isEmpty() || actorRequestMembershipOpt.isEmpty()) {
            return failure(ERROR_MEMBERSHIP_NOT_FOUND, "parentAccess.membershipNotFound");
        }
        var target = targetOpt.get();
        var originalActor = actorRequestMembershipOpt.get();

        target.setPermission(FamilyParentMembershipEntity.Permission.family_admin);
        originalActor.setPermission(FamilyParentMembershipEntity.Permission.editor);
        request.setStatus(FamilyAdminTransferRequestEntity.Status.accepted);
        request.setRespondedAt(Instant.now());

        cancelOtherPending(familyDbId, requestId);
        var parent = parentAccountRepository.findByIdOptional(target.getParentAccountId()).orElse(null);
        var identity = telegramIdentityRepository.findActiveParentByParentAccountId(target.getParentAccountId())
            .orElse(null);

        log.info("Accepted admin transfer request: id={}, targetMembershipId={}, familyId={}",
            requestId, request.getTargetMembershipId(), familyId);

        return OperationResult.success(toDto(target, parent, identity));
    }

    @Override
    @Transactional
    public OperationResult<ParentMembershipDto> declineTransferRequest(
        Integer requestId, String familyId, Integer actorParentAccountId, String actorEmail) {
        var familyOpt = resolveFamily(familyId);
        if (familyOpt.isEmpty()) {
            return failure(ERROR_FAMILY_NOT_FOUND, "family.familyNotFound");
        }

        var requestOpt = transferRequestRepository.findByIdOptional(requestId);
        if (requestOpt.isEmpty()) {
            return failure(ERROR_TRANSFER_NOT_FOUND, "parentAccess.transferRequestNotFound");
        }
        var request = requestOpt.get();
        Integer familyDbId = familyOpt.get().getId();
        if (!request.getFamilyId().equals(familyDbId)) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }

        var actorMembershipOpt = FamilyParentActorResolver.resolve(
            familyDbId, actorParentAccountId, actorEmail, parentAccountRepository, membershipRepository);
        if (actorMembershipOpt.isEmpty()) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }
        var actorMembership = actorMembershipOpt.get();
        if (!actorMembership.getId().equals(request.getTargetMembershipId())) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }

        if (request.getStatus() != FamilyAdminTransferRequestEntity.Status.pending) {
            return failure(ERROR_TRANSFER_NOT_PENDING, "parentAccess.transferRequestNotPending");
        }

        request.setStatus(FamilyAdminTransferRequestEntity.Status.declined);
        request.setRespondedAt(Instant.now());

        var targetOpt = membershipRepository.findByIdOptional(request.getTargetMembershipId());
        var parent = targetOpt.isPresent()
            ? parentAccountRepository.findByIdOptional(targetOpt.get().getParentAccountId()).orElse(null)
            : null;
        var identity = targetOpt.isPresent()
            ? telegramIdentityRepository.findActiveParentByParentAccountId(
                targetOpt.get().getParentAccountId()).orElse(null)
            : null;

        log.info("Declined admin transfer request: id={}, targetMembershipId={}, familyId={}",
            requestId, request.getTargetMembershipId(), familyId);

        return OperationResult.success(toDto(targetOpt.orElse(null), parent, identity));
    }

    private void cancelOtherPending(Integer familyDbId, Integer excludeRequestId) {
        transferRequestRepository.findPendingByFamilyAll(familyDbId).stream()
            .filter(request -> !request.getId().equals(excludeRequestId))
            .forEach(request -> {
                request.setStatus(FamilyAdminTransferRequestEntity.Status.cancelled);
                request.setCancelledAt(Instant.now());
            });
    }

    @Override
    @Transactional
    public OperationResult<ParentMembershipDto> cancelTransferRequest(
        Integer requestId, String familyId, Integer actorParentAccountId, String actorEmail) {
        var familyOpt = resolveFamily(familyId);
        if (familyOpt.isEmpty()) {
            return failure(ERROR_FAMILY_NOT_FOUND, "family.familyNotFound");
        }

        var requestOpt = transferRequestRepository.findByIdOptional(requestId);
        if (requestOpt.isEmpty()) {
            return failure(ERROR_TRANSFER_NOT_FOUND, "parentAccess.transferRequestNotFound");
        }
        var request = requestOpt.get();
        Integer familyDbId = familyOpt.get().getId();
        if (!request.getFamilyId().equals(familyDbId)) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }

        var actorMembershipOpt = FamilyParentActorResolver.resolve(
            familyDbId, actorParentAccountId, actorEmail, parentAccountRepository, membershipRepository);
        if (actorMembershipOpt.isEmpty()) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }
        var actorMembership = actorMembershipOpt.get();
        if (!actorMembership.getId().equals(request.getActorMembershipId())) {
            return failure(ERROR_NOT_AUTHORIZED, "parentAccess.notAuthorized");
        }

        if (request.getStatus() != FamilyAdminTransferRequestEntity.Status.pending) {
            return failure(ERROR_TRANSFER_NOT_PENDING, "parentAccess.transferRequestNotPending");
        }

        request.setStatus(FamilyAdminTransferRequestEntity.Status.cancelled);
        request.setCancelledAt(Instant.now());

        var targetOpt = membershipRepository.findByIdOptional(request.getTargetMembershipId());
        var parent = targetOpt.isPresent()
            ? parentAccountRepository.findByIdOptional(targetOpt.get().getParentAccountId()).orElse(null)
            : null;
        var identity = targetOpt.isPresent()
            ? telegramIdentityRepository.findActiveParentByParentAccountId(
                targetOpt.get().getParentAccountId()).orElse(null)
            : null;

        log.info("Cancelled admin transfer request: id={}, targetMembershipId={}, familyId={}",
            requestId, request.getTargetMembershipId(), familyId);

        return OperationResult.success(toDto(targetOpt.orElse(null), parent, identity));
    }

    private String membershipName(FamilyParentMembershipEntity membership,
                                  ParentAccountRepository repository) {
        if (membership == null) {
            return null;
        }
        if (membership.getDisplayName() != null && !membership.getDisplayName().isBlank()) {
            return membership.getDisplayName();
        }
        return repository.findByIdOptional(membership.getParentAccountId())
            .map(ParentAccountEntity::getEmail)
            .orElse(null);
    }
}
