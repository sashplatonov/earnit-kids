package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.family.api.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyAdminTransferRequestEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.MembershipStatus;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyAdminTransferRequestRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.identity.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class FamilyParentTransferOperations {
  private static final String FAMILY_NOT_FOUND = "FAMILY_NOT_FOUND";
  private static final String MEMBERSHIP_NOT_FOUND = "PARENT_MEMBERSHIP_NOT_FOUND";
  private static final String FORBIDDEN = "PARENT_MEMBERSHIP_FORBIDDEN";
  private static final String ADMIN_DELETE_FORBIDDEN = "PARENT_ADMIN_DELETE_FORBIDDEN";
  private static final String TRANSFER_NOT_FOUND = "PARENT_TRANSFER_REQUEST_NOT_FOUND";
  private static final String TRANSFER_NOT_PENDING = "PARENT_TRANSFER_REQUEST_NOT_PENDING";
  private static final String TRANSFER_PENDING_EXISTS = "PARENT_TRANSFER_REQUEST_PENDING_EXISTS";

  private final Supplier<FamilyRepository> familyRepository;
  private final ParentAccountRepository parentAccountRepository;
  private final FamilyParentMembershipRepository membershipRepository;
  private final FamilyAdminTransferRequestRepository transferRequestRepository;
  private final TelegramIdentityRepository telegramIdentityRepository;

  public FamilyParentTransferOperations(
      FamilyRepository familyRepository,
      ParentAccountRepository parentAccountRepository,
      FamilyParentMembershipRepository membershipRepository,
      FamilyAdminTransferRequestRepository transferRequestRepository,
      TelegramIdentityRepository telegramIdentityRepository) {
    this.familyRepository = () -> familyRepository;
    this.parentAccountRepository = parentAccountRepository;
    this.membershipRepository = membershipRepository;
    this.transferRequestRepository = transferRequestRepository;
    this.telegramIdentityRepository = telegramIdentityRepository;
  }

  @Transactional
  public OperationResult<ParentMembershipDto> transferAdmin(
      Integer membershipId, String familyId, Integer actorParentAccountId, String actorEmail) {
    return createTransferRequest(membershipId, familyId, actorParentAccountId, actorEmail);
  }

  @Transactional
  public OperationResult<ParentMembershipDto> createTransferRequest(
      Integer targetMembershipId,
      String familyId,
      Integer actorParentAccountId,
      String actorEmail) {
    var familyOpt = resolveFamily(familyId);
    if (familyOpt.isEmpty()) {
      return failure(FAMILY_NOT_FOUND, "family.familyNotFound");
    }
    var membershipOpt = membershipRepository.findByIdOptional(targetMembershipId);
    if (membershipOpt.isEmpty()) {
      return failure(MEMBERSHIP_NOT_FOUND, "parentAccess.membershipNotFound");
    }
    var target = membershipOpt.get();
    Integer familyDbId = familyOpt.get().getId();
    if (!target.getFamilyId().equals(familyDbId)
        || target.getStatus() != MembershipStatus.active
        || target.getPermission() == FamilyParentMembershipEntity.Permission.family_admin) {
      return failure(FORBIDDEN, "parentAccess.notAuthorized");
    }
    var actorOpt =
        FamilyParentActorResolver.resolve(
            familyDbId,
            actorParentAccountId,
            actorEmail,
            parentAccountRepository,
            membershipRepository);
    if (actorOpt.isEmpty()) {
      return failure(FORBIDDEN, "parentAccess.notAuthorized");
    }
    var actor = actorOpt.get();
    if (actor.getPermission() != FamilyParentMembershipEntity.Permission.family_admin) {
      return failure(ADMIN_DELETE_FORBIDDEN, "parentAccess.cannotRemoveAdmin");
    }
    if (transferRequestRepository.findPendingByFamily(familyDbId).isPresent()) {
      return failure(TRANSFER_PENDING_EXISTS, "parentAccess.transferRequestPendingExists");
    }
    var request =
        FamilyAdminTransferRequestEntity.builder()
            .familyId(familyDbId)
            .actorMembershipId(actor.getId())
            .targetMembershipId(target.getId())
            .status(FamilyAdminTransferRequestEntity.Status.pending)
            .build();
    transferRequestRepository.persist(request);
    return OperationResult.success(toTransferDto(target, request, actor));
  }

  @Transactional
  public OperationResult<ParentMembershipDto> acceptTransferRequest(
      Integer requestId, String familyId, Integer actorParentAccountId, String actorEmail) {
    var context = resolveRequest(requestId, familyId, actorParentAccountId, actorEmail);
    if (context.error() != null) {
      return context.error();
    }
    var request = context.request();
    var actor = context.actor();
    if (!actor.getId().equals(request.getTargetMembershipId())) {
      return failure(FORBIDDEN, "parentAccess.notAuthorized");
    }
    if (request.getStatus() != FamilyAdminTransferRequestEntity.Status.pending) {
      return failure(TRANSFER_NOT_PENDING, "parentAccess.transferRequestNotPending");
    }
    var targetOpt = membershipRepository.findByIdOptional(request.getTargetMembershipId());
    var originalOpt = membershipRepository.findByIdOptional(request.getActorMembershipId());
    if (targetOpt.isEmpty() || originalOpt.isEmpty()) {
      return failure(MEMBERSHIP_NOT_FOUND, "parentAccess.membershipNotFound");
    }
    targetOpt.get().setPermission(FamilyParentMembershipEntity.Permission.family_admin);
    originalOpt.get().setPermission(FamilyParentMembershipEntity.Permission.editor);
    request.setStatus(FamilyAdminTransferRequestEntity.Status.accepted);
    request.setRespondedAt(Instant.now());
    cancelOtherPending(context.familyDbId(), requestId);
    return OperationResult.success(toDto(targetOpt.get()));
  }

  @Transactional
  public OperationResult<ParentMembershipDto> declineTransferRequest(
      Integer requestId, String familyId, Integer actorParentAccountId, String actorEmail) {
    var context = resolveRequest(requestId, familyId, actorParentAccountId, actorEmail);
    if (context.error() != null) {
      return context.error();
    }
    var request = context.request();
    if (!context.actor().getId().equals(request.getTargetMembershipId())) {
      return failure(FORBIDDEN, "parentAccess.notAuthorized");
    }
    if (request.getStatus() != FamilyAdminTransferRequestEntity.Status.pending) {
      return failure(TRANSFER_NOT_PENDING, "parentAccess.transferRequestNotPending");
    }
    request.setStatus(FamilyAdminTransferRequestEntity.Status.declined);
    request.setRespondedAt(Instant.now());
    return membershipDto(request.getTargetMembershipId());
  }

  @Transactional
  public OperationResult<ParentMembershipDto> cancelTransferRequest(
      Integer requestId, String familyId, Integer actorParentAccountId, String actorEmail) {
    var context = resolveRequest(requestId, familyId, actorParentAccountId, actorEmail);
    if (context.error() != null) {
      return context.error();
    }
    var request = context.request();
    if (!context.actor().getId().equals(request.getActorMembershipId())) {
      return failure(FORBIDDEN, "parentAccess.notAuthorized");
    }
    if (request.getStatus() != FamilyAdminTransferRequestEntity.Status.pending) {
      return failure(TRANSFER_NOT_PENDING, "parentAccess.transferRequestNotPending");
    }
    request.setStatus(FamilyAdminTransferRequestEntity.Status.cancelled);
    request.setCancelledAt(Instant.now());
    return membershipDto(request.getTargetMembershipId());
  }

  private FamilyParentTransferRequestContext resolveRequest(
      Integer requestId, String familyId, Integer actorParentAccountId, String actorEmail) {
    var familyOpt = resolveFamily(familyId);
    if (familyOpt.isEmpty()) {
      return FamilyParentTransferRequestContext.error(
          failure(FAMILY_NOT_FOUND, "family.familyNotFound"));
    }
    var requestOpt = transferRequestRepository.findByIdOptional(requestId);
    if (requestOpt.isEmpty()) {
      return FamilyParentTransferRequestContext.error(
          failure(TRANSFER_NOT_FOUND, "parentAccess.transferRequestNotFound"));
    }
    Integer familyDbId = familyOpt.get().getId();
    var request = requestOpt.get();
    if (!request.getFamilyId().equals(familyDbId)) {
      return FamilyParentTransferRequestContext.error(
          failure(FORBIDDEN, "parentAccess.notAuthorized"));
    }
    var actorOpt =
        FamilyParentActorResolver.resolve(
            familyDbId,
            actorParentAccountId,
            actorEmail,
            parentAccountRepository,
            membershipRepository);
    if (actorOpt.isEmpty()) {
      return FamilyParentTransferRequestContext.error(
          failure(FORBIDDEN, "parentAccess.notAuthorized"));
    }
    return new FamilyParentTransferRequestContext(familyDbId, request, actorOpt.get(), null);
  }

  private OperationResult<ParentMembershipDto> membershipDto(Integer membershipId) {
    var membership = membershipRepository.findByIdOptional(membershipId).orElse(null);
    if (membership == null) {
      return failure(MEMBERSHIP_NOT_FOUND, "parentAccess.membershipNotFound");
    }
    return OperationResult.success(toDto(membership));
  }

  private void cancelOtherPending(Integer familyDbId, Integer excludeRequestId) {
    transferRequestRepository.findPendingByFamilyAll(familyDbId).stream()
        .filter(request -> !request.getId().equals(excludeRequestId))
        .forEach(
            request -> {
              request.setStatus(FamilyAdminTransferRequestEntity.Status.cancelled);
              request.setCancelledAt(Instant.now());
            });
  }

  private ParentMembershipDto toDto(FamilyParentMembershipEntity membership) {
    ParentAccountEntity parent =
        parentAccountRepository.findByIdOptional(membership.getParentAccountId()).orElse(null);
    TelegramIdentityEntity identity =
        telegramIdentityRepository
            .findActiveParentByParentAccountId(membership.getParentAccountId())
            .orElse(null);
    return new ParentMembershipDto(
        membership.getId(),
        parent == null ? null : parent.getEmail(),
        membership.getDisplayName(),
        identity == null ? null : identity.getTelegramUserId(),
        identity == null ? null : identity.getTelegramUsername(),
        identity == null ? null : identity.getTelegramDisplayName(),
        membership.getPermission(),
        membership.getStatus());
  }

  private ParentMembershipDto toTransferDto(
      FamilyParentMembershipEntity membership,
      FamilyAdminTransferRequestEntity request,
      FamilyParentMembershipEntity actor) {
    ParentMembershipDto dto = toDto(membership);
    return new ParentMembershipDto(
        dto.id(),
        dto.email(),
        dto.displayName(),
        dto.telegramUserId(),
        dto.telegramUsername(),
        dto.telegramDisplayName(),
        dto.permission(),
        dto.status(),
        null,
        "pending",
        actor.getDisplayName(),
        membership.getDisplayName(),
        request.getId(),
        "target");
  }

  private Optional<FamilyEntity> resolveFamily(String familyId) {
    return familyRepository.get().findById(familyId);
  }

  private <T> OperationResult<T> failure(String code, String messageKey) {
    return OperationResult.failure(code, BackendMessages.message(messageKey));
  }
}
