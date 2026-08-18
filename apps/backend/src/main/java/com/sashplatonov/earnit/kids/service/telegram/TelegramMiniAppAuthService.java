package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.ParentAccountRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

// EXPLAIN: Admin access service to detect Telegram admin IDs from configuration
import com.sashplatonov.earnit.kids.service.admin.AdminAccessService;

@ApplicationScoped
public class TelegramMiniAppAuthService {
    private static final Logger LOG = Logger.getLogger(TelegramMiniAppAuthService.class);
    private static final String AUTH_FAILED = "Telegram authentication failed.";

    private final TelegramInitDataVerifier verifier;
    private final TelegramIdentityRepository identities;
    private final TelegramIdentityService identityService;
    private final TimeProvider timeProvider;
    @Inject
    FamilyRepository families;
    @Inject
    ChildRepository children;
    @Inject
    ParentAccountRepository parents;
    @Inject
    FamilyParentMembershipRepository memberships;

    @Inject
    AdminAccessService adminAccessService;

    @Inject
    public TelegramMiniAppAuthService(TelegramInitDataVerifier verifier,
                                      TelegramIdentityRepository identities,
                                      TelegramIdentityService identityService,
                                      TimeProvider timeProvider) {
        this.verifier = verifier;
        this.identities = identities;
        this.identityService = identityService;
        this.timeProvider = timeProvider;
    }

    public OperationResult<AuthPayload> authenticate(String rawInitData) {
        LOG.debugf("Starting authentication for rawInitData=%s", rawInitData);
        return authenticate(rawInitData, null);
    }

    // EXPLAIN: A sign-in via the child-invite deep link carries the pairing
    // EXPLAIN: token as startapp. Accepting it first binds the child's Telegram
    // EXPLAIN: account so the subsequent identity lookup succeeds.
    public OperationResult<AuthPayload> authenticate(String rawInitData, String pairingToken) {
        var verified = verifier.verify(rawInitData);
        LOG.debug("Verification result: " + verified);
        if (verified.isEmpty()) {
            LOG.warn("Verification failed for rawInitData");
            return OperationResult.failure("TELEGRAM_AUTH_FAILED", AUTH_FAILED);
        }
        if (pairingToken != null && !pairingToken.isBlank() && pairingToken.startsWith(TelegramInviteToken.CHILD_INVITE_PREFIX)) {
            String token = pairingToken.substring(TelegramInviteToken.CHILD_INVITE_PREFIX.length());
            identityService.acceptChildInvitation(token, verified.get().telegramUserId(), timeProvider.now());
        }
        TelegramIdentityEntity identity = identities.findActiveByTelegramUserId(verified.get().telegramUserId())
            .orElse(null);
        if (identity == null) {
            LOG.warnf("No active Telegram identity found for userId=%d", verified.get().telegramUserId());
            return OperationResult.failure("TELEGRAM_IDENTITY_UNLINKED", AUTH_FAILED);
        }
        var family = identity.getFamilyId() == null ? null : families.findByDbId(identity.getFamilyId()).orElse(null);
        if (family == null || family.isBlocked()) {
            LOG.warnf("Family not found or blocked for familyId=%s", identity.getFamilyId());
            return OperationResult.failure("TELEGRAM_AUTH_FAILED", AUTH_FAILED);
        }

        // EXPLAIN: Admin check – if Telegram ID is in TELEGRAM_ADMIN_USER_IDS, grant admin access
        if (adminAccessService != null && adminAccessService.isAdmin(identity.getTelegramUserId())) {
            LOG.infof("Telegram user %d identified as admin", identity.getTelegramUserId());
            // EXPLAIN: No linked parent account; use placeholder values where appropriate
            AuthPayload adminPayload = new AuthPayload(
                family.getFamilyId(),
                null,
                "admin",
                null,
                null,
                false,
                "family_admin",
                null,
                false);
            LOG.debugf("Returning admin AuthPayload: %s", adminPayload);
            return OperationResult.success(adminPayload);
        }

        LOG.debugf("Proceeding with role-based authentication, role=%s", identity.getRole());
        if ("parent".equals(identity.getRole())) {
            return authenticateParent(identity, family);
        }
        return authenticateChild(identity, family);
    }

    private OperationResult<AuthPayload> authenticateParent(TelegramIdentityEntity identity, FamilyEntity family) {
        if (identity.getParentAccountId() == null) {
            return failed();
        }
        var parent = parents.findByIdOptional(identity.getParentAccountId()).orElse(null);
        var membership = parent == null ? null
            : memberships.findByParentAndFamily(parent.getId(), family.getId()).orElse(null);
        if (parent == null || parent.isBlocked() || membership == null) {
            return failed();
        }
        return OperationResult.success(new AuthPayload(
            family.getFamilyId(), parent.getEmail(), "admin", null, null, false,
            membership.getPermission().name(), null, false));
    }

    private OperationResult<AuthPayload> authenticateChild(TelegramIdentityEntity identity, FamilyEntity family) {
        if (!"child".equals(identity.getRole()) || identity.getChildId() == null) {
            return failed();
        }
        var child = children.findByIdOptional(identity.getChildId()).orElse(null);
        if (child == null || !identity.getFamilyId().equals(child.getFamilyDbId())) {
            return failed();
        }
        return OperationResult.success(new AuthPayload(
            family.getFamilyId(), family.getEmail(), "child", child.getId(), child.getName(),
            false, "child", null, false));
    }

    private OperationResult<AuthPayload> failed() {
        return OperationResult.failure("TELEGRAM_AUTH_FAILED", AUTH_FAILED);
    }
}
