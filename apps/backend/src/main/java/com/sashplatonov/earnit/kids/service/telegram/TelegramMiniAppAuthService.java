package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.dto.response.AuthPayload;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TelegramMiniAppAuthService {
    private static final String AUTH_FAILED = "Telegram authentication failed.";

    private final TelegramInitDataVerifier verifier;
    private final TelegramIdentityRepository identities;
    @Inject
    FamilyRepository families;
    @Inject
    ChildRepository children;

    @Inject
    public TelegramMiniAppAuthService(TelegramInitDataVerifier verifier,
                                      TelegramIdentityRepository identities) {
        this.verifier = verifier;
        this.identities = identities;
    }

    public OperationResult<AuthPayload> authenticate(String rawInitData) {
        var verified = verifier.verify(rawInitData);
        if (verified.isEmpty()) {
            return OperationResult.failure("TELEGRAM_AUTH_FAILED", AUTH_FAILED);
        }
        TelegramIdentityEntity identity = identities.findActiveByTelegramUserId(verified.get().telegramUserId())
            .orElse(null);
        if (identity == null) {
            return OperationResult.failure("TELEGRAM_IDENTITY_UNLINKED", AUTH_FAILED);
        }
        if (identity.getFamilyId() == null) {
            return OperationResult.failure("TELEGRAM_AUTH_FAILED", AUTH_FAILED);
        }
        var family = families.findByDbId(identity.getFamilyId()).orElse(null);
        if (family == null || family.isBlocked()) {
            return OperationResult.failure("TELEGRAM_AUTH_FAILED", AUTH_FAILED);
        }
        if ("parent".equals(identity.getRole())) {
            return OperationResult.success(new AuthPayload(
                family.getFamilyId(), family.getEmail(), "admin", null, null, false, "family_admin", null, false));
        }
        if (!"child".equals(identity.getRole()) || identity.getChildId() == null) {
            return OperationResult.failure("TELEGRAM_AUTH_FAILED", AUTH_FAILED);
        }
        var child = children.findByIdOptional(identity.getChildId()).orElse(null);
        if (child == null || !identity.getFamilyId().equals(child.getFamilyDbId())) {
            return OperationResult.failure("TELEGRAM_AUTH_FAILED", AUTH_FAILED);
        }
        return OperationResult.success(new AuthPayload(
            family.getFamilyId(), family.getEmail(), "child", child.getId(), child.getName(),
            false, "child", null, false));
    }
}
