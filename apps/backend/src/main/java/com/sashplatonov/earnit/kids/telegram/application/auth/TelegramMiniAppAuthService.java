package com.sashplatonov.earnit.kids.telegram.application.auth;

import com.sashplatonov.earnit.kids.admin.application.AdminAccessService;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramIdentityService;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramInitDataVerifier;
import com.sashplatonov.earnit.kids.telegram.application.invitation.TelegramInviteToken;
import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TelegramMiniAppAuthService {
  private static final Logger LOG = Logger.getLogger(TelegramMiniAppAuthService.class);
  private static final String AUTH_FAILED = "Telegram authentication failed.";

  private final TelegramInitDataVerifier verifier;
  private final TelegramIdentityRepository identities;
  private final TelegramIdentityService identityService;
  private final TimeProvider timeProvider;
  FamilyRepository families;
  ChildRepository children;
  ParentAccountRepository parents;
  FamilyParentMembershipRepository memberships;
  AdminAccessService adminAccessService;

  public TelegramMiniAppAuthService(
      TelegramInitDataVerifier verifier,
      TelegramIdentityRepository identities,
      TelegramIdentityService identityService,
      TimeProvider timeProvider) {
    this(verifier, identities, identityService, timeProvider, null, null, null, null, null);
  }

  @Inject
  public TelegramMiniAppAuthService(
      TelegramInitDataVerifier verifier,
      TelegramIdentityRepository identities,
      TelegramIdentityService identityService,
      TimeProvider timeProvider,
      FamilyRepository families,
      ChildRepository children,
      ParentAccountRepository parents,
      FamilyParentMembershipRepository memberships,
      AdminAccessService adminAccessService) {
    this.verifier = verifier;
    this.identities = identities;
    this.identityService = identityService;
    this.timeProvider = timeProvider;
    this.families = families;
    this.children = children;
    this.parents = parents;
    this.memberships = memberships;
    this.adminAccessService = adminAccessService;
  }

  public OperationResult<AuthPayload> authenticate(String rawInitData) {
    LOG.debugf("Starting authentication for rawInitData=%s", rawInitData);
    return authenticate(rawInitData, null);
  }

  @Transactional
  public OperationResult<AuthPayload> authenticate(String rawInitData, String pairingToken) {
    var verified = verifier.verify(rawInitData);
    LOG.debug("Verification result: " + verified);
    if (verified.isEmpty()) {
      LOG.warn("Verification failed for rawInitData");
      return OperationResult.failure("TELEGRAM_AUTH_FAILED", AUTH_FAILED);
    }
    acceptPairing(pairingToken, verified.get().telegramUserId());
    TelegramIdentityEntity identity =
        identities.findActiveByTelegramUserId(verified.get().telegramUserId()).orElse(null);
    if (identity == null) {
      LOG.warnf("No active Telegram identity found for userId=%d", verified.get().telegramUserId());
      return OperationResult.failure("TELEGRAM_IDENTITY_UNLINKED", AUTH_FAILED);
    }
    refreshTelegramProfile(identity, verified.get());
    return authenticateIdentity(identity, verified.get());
  }

  private void acceptPairing(String pairingToken, long telegramUserId) {
    if (pairingToken == null
        || pairingToken.isBlank()
        || !pairingToken.startsWith(TelegramInviteToken.CHILD_INVITE_PREFIX)) {
      return;
    }
    String token = pairingToken.substring(TelegramInviteToken.CHILD_INVITE_PREFIX.length());
    identityService.acceptChildInvitation(token, telegramUserId, timeProvider.now());
  }

  private OperationResult<AuthPayload> authenticateIdentity(
      TelegramIdentityEntity identity, TelegramInitDataVerifier.VerifiedInitData verified) {
    var family =
        identity.getFamilyId() == null
            ? null
            : families.findByDbId(identity.getFamilyId()).orElse(null);
    if (family == null || family.isBlocked()) {
      LOG.warnf("Family not found or blocked for familyId=%s", identity.getFamilyId());
      return OperationResult.failure("TELEGRAM_AUTH_FAILED", AUTH_FAILED);
    }

    if (isConfigAdmin(identity)) {
      return authenticateAdmin(identity, family);
    }
    LOG.infof(
        "Telegram user %d is NOT an admin (not in admin-user-ids list), "
            + "proceeding with role=%s",
        identity.getTelegramUserId(), identity.getRole());

    LOG.debugf("Proceeding with role-based authentication, role=%s", identity.getRole());
    if ("parent".equals(identity.getRole())) {
      return authenticateParent(identity, family);
    }
    return authenticateChild(identity, family);
  }

  private boolean isConfigAdmin(TelegramIdentityEntity identity) {
    return adminAccessService != null && adminAccessService.isAdmin(identity.getTelegramUserId());
  }

  private OperationResult<AuthPayload> authenticateAdmin(
      TelegramIdentityEntity identity, FamilyEntity family) {
    if ("parent".equals(identity.getRole()) && identity.getParentAccountId() != null) {
      LOG.infof("Telegram user %d is admin+parent, using normal parent auth", identity.getTelegramUserId());
      return authenticateParent(identity, family);
    }
    LOG.infof(
        "Telegram user %d identified as admin (non-parent identity), granting admin role",
        identity.getTelegramUserId());
    String adminEmail = resolveAdminEmail(identity, family);
    AuthPayload adminPayload =
        new AuthPayload(family.getFamilyId(), adminEmail, "admin", null, null, "family_admin", null, false);
    return OperationResult.success(adminPayload);
  }

  private void refreshTelegramProfile(
      TelegramIdentityEntity identity, TelegramInitDataVerifier.VerifiedInitData verified) {
    if (verified.telegramUsername() != null && !verified.telegramUsername().isBlank()) {
      identity.setTelegramUsername(verified.telegramUsername());
    }
    if (verified.telegramDisplayName() != null && !verified.telegramDisplayName().isBlank()) {
      identity.setTelegramDisplayName(verified.telegramDisplayName());
    }
  }

  private OperationResult<AuthPayload> authenticateParent(
      TelegramIdentityEntity identity, FamilyEntity family) {
    if (identity.getParentAccountId() == null) {
      return failed();
    }
    var parent = parents.findByIdOptional(identity.getParentAccountId()).orElse(null);
    var membership =
        parent == null
            ? null
            : memberships.findByParentAndFamily(parent.getId(), family.getId()).orElse(null);
    if (parent == null || parent.isBlocked() || membership == null) {
      return failed();
    }
    return OperationResult.success(
        new AuthPayload(
            family.getFamilyId(),
            parent.getEmail(),
            "admin",
            null,
            null,
            membership.getPermission().name(),
            null,
            false,
            parent.getId()));
  }

  private OperationResult<AuthPayload> authenticateChild(
      TelegramIdentityEntity identity, FamilyEntity family) {
    if (!"child".equals(identity.getRole()) || identity.getChildId() == null) {
      return failed();
    }
    var child = children.findByIdOptional(identity.getChildId()).orElse(null);
    if (child == null || !identity.getFamilyId().equals(child.getFamilyDbId())) {
      return failed();
    }
    return OperationResult.success(
        new AuthPayload(
            family.getFamilyId(),
            family.getEmail(),
            "child",
            child.getId(),
            child.getName(),
            "child",
            null,
            false));
  }

  private OperationResult<AuthPayload> failed() {
    return OperationResult.failure("TELEGRAM_AUTH_FAILED", AUTH_FAILED);
  }

  private String resolveAdminEmail(TelegramIdentityEntity identity, FamilyEntity family) {
    if (identity.getParentAccountId() != null) {
      var parent = parents.findByIdOptional(identity.getParentAccountId()).orElse(null);
      if (parent != null && !parent.isBlocked()) {
        return parent.getEmail();
      }
    }
    var familyAdmins = memberships.findByFamilyId(family.getId());
    for (var membership : familyAdmins) {
      var parent = parents.findByIdOptional(membership.getParentAccountId()).orElse(null);
      if (parent != null && !parent.isBlocked()) {
        return parent.getEmail();
      }
    }
    LOG.warnf("No parent email found for admin session, familyId=%s", family.getFamilyId());
    return null;
  }
}
