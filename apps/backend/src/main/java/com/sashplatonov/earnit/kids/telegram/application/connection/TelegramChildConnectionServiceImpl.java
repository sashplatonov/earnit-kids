package com.sashplatonov.earnit.kids.telegram.application.connection;

import com.sashplatonov.earnit.kids.family.application.membership.ChildOwnershipService;
import com.sashplatonov.earnit.kids.family.domain.model.child.ChildEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.telegram.api.response.ChildTelegramConnectionResponse;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramIdentityService;
import com.sashplatonov.earnit.kids.telegram.application.invitation.TelegramInviteToken;
import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.ServiceResults;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class TelegramChildConnectionServiceImpl implements TelegramChildConnectionService {
  private static final long INVITE_TTL_SECONDS = 900;
  private static final String UNAVAILABLE = "Telegram linking is not configured.";

  private FamilyRepository families;
  private TelegramIdentityRepository identities;
  private TelegramIdentityService identityService;
  private TelegramConfig config;
  private ChildOwnershipService childOwnershipService;
  private TimeProvider timeProvider;

  TelegramChildConnectionServiceImpl() {}

  @Inject
  TelegramChildConnectionServiceImpl(
      FamilyRepository families,
      TelegramIdentityRepository identities,
      TelegramIdentityService identityService,
      TelegramConfig config,
      ChildOwnershipService childOwnershipService,
      TimeProvider timeProvider) {
    this.families = families;
    this.identities = identities;
    this.identityService = identityService;
    this.config = config;
    this.childOwnershipService = childOwnershipService;
    this.timeProvider = timeProvider;
  }

  @Override
  public OperationResult<ChildTelegramConnectionResponse> connection(String familyId, int childId) {
    var child = familyChild(familyId, childId);
    if (child.isEmpty()) {
      return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
    }
    Optional<TelegramIdentityEntity> identity = identities.findActiveChild(childId);
    return OperationResult.success(
        new ChildTelegramConnectionResponse(
            childId,
            identity.isPresent(),
            identity.map(TelegramIdentityEntity::getTelegramUserId).orElse(null)));
  }

  @Override
  @Transactional
  public OperationResult<TelegramLinkLaunchResponse> invite(String familyId, int childId) {
    var child = familyChild(familyId, childId);
    if (child.isEmpty()) {
      return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
    }
    String botUsername = config.botUsername().filter(value -> !value.isBlank()).orElse(null);
    if (botUsername == null) {
      return ServiceResults.failure("TELEGRAM_LINK_UNAVAILABLE", UNAVAILABLE);
    }
    if (identities.findActiveChild(childId).isPresent()) {
      return ServiceResults.failure(
          "TELEGRAM_ALREADY_LINKED", "This child already has a linked Telegram account.");
    }

    Instant now = timeProvider.now();
    var token =
        identityService.issueChildInvitation(
            child.get().getFamilyDbId(),
            childId,
            "parent",
            now.plusSeconds(INVITE_TTL_SECONDS),
            now);
    return OperationResult.success(
        new TelegramLinkLaunchResponse(
            "https:"
                + '/'
                + '/'
                + "t.me/"
                + botUsername
                + "?startapp="
                + TelegramInviteToken.CHILD_INVITE_PREFIX
                + token.token()));
  }

  @Override
  @Transactional
  public OperationResult<Void> unlink(String familyId, int childId) {
    var child = familyChild(familyId, childId);
    if (child.isEmpty()) {
      return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
    }
    Optional<TelegramIdentityEntity> identity = identities.findActiveChild(childId);
    if (identity.isEmpty()) {
      return OperationResult.success(null);
    }
    identityService.unlink(identity.get().getTelegramUserId(), "parent", timeProvider.now());
    return OperationResult.success(null);
  }

  private Optional<ChildEntity> familyChild(String familyId, int childId) {
    Optional<Integer> dbIdOpt = families.getDbId(familyId);
    if (dbIdOpt.isEmpty()) {
      return Optional.empty();
    }
    return childOwnershipService.findFamilyChild(dbIdOpt.get(), childId);
  }
}
