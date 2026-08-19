package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.dto.response.ChildTelegramConnectionResponse;
import com.sashplatonov.earnit.kids.dto.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.service.common.ServiceResults;
import com.sashplatonov.earnit.kids.service.family.ChildOwnershipService;
import com.sashplatonov.earnit.kids.util.OperationResult;
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

    @Inject private FamilyRepository families;
    @Inject private TelegramIdentityRepository identities;
    @Inject private TelegramIdentityService identityService;
    @Inject private TelegramConfig config;
    @Inject private ChildOwnershipService childOwnershipService;
    @Inject private TimeProvider timeProvider;

    TelegramChildConnectionServiceImpl() {
    }

    @Override
    public OperationResult<ChildTelegramConnectionResponse> connection(String familyId, int childId) {
        var child = familyChild(familyId, childId);
        if (child.isEmpty()) {
            return ServiceResults.failure("CHILD_NOT_FOUND", "family.childNotFound");
        }
        Optional<TelegramIdentityEntity> identity = identities.findActiveChild(childId);
        return OperationResult.success(new ChildTelegramConnectionResponse(
            childId,
            identity.isPresent(),
            identity.map(TelegramIdentityEntity::getTelegramUserId).orElse(null)
        ));
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
            return ServiceResults.failure("TELEGRAM_ALREADY_LINKED", "This child already has a linked Telegram account.");
        }

        Instant now = timeProvider.now();
        var token = identityService.issueChildInvitation(
            child.get().getFamilyDbId(),
            childId,
            "parent",
            now.plusSeconds(INVITE_TTL_SECONDS),
            now
        );
        return OperationResult.success(new TelegramLinkLaunchResponse(
            "https:" + '/' + '/' + "t.me/" + botUsername + "?startapp=" + TelegramInviteToken.CHILD_INVITE_PREFIX + token.token()
        ));
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
