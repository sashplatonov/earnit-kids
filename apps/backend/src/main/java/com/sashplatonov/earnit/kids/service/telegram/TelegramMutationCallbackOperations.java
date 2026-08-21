package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.TelegramCallbackActionEntity;
import com.sashplatonov.earnit.kids.repository.TelegramCallbackActionRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;

import java.time.Instant;
import java.util.Optional;

final class TelegramMutationCallbackOperations {
    private final TelegramIdentityRepository identities;
    private final TelegramCallbackActionRepository callbacks;
    private final SecureTokenGenerator tokens;

    TelegramMutationCallbackOperations(TelegramIdentityRepository identities,
                                       TelegramCallbackActionRepository callbacks,
                                       SecureTokenGenerator tokens) {
        this.identities = identities;
        this.callbacks = callbacks;
        this.tokens = tokens;
    }

    TelegramIdentityService.TelegramCallbackToken create(
        Integer familyId, Integer identityId, String action, long targetId, Instant expiresAt, Instant now) {
        requireFutureExpiry(expiresAt, now);
        if (!action.equals("APPROVE") && !action.equals("REJECT") && !action.equals("ADJUST_BALANCE")) {
            throw new IllegalArgumentException("Unsupported callback action");
        }
        if (identities.findActiveParentByIdAndFamilyId(identityId, familyId).isEmpty()) {
            throw new IllegalArgumentException("Telegram identity cannot perform mutations for family");
        }
        String token = tokens.generateHexToken(24);
        TelegramCallbackActionEntity callback = TelegramCallbackActionEntity.builder()
            .familyId(familyId).identityId(identityId).action(action).targetId(targetId)
            .secretDigest(TelegramTokenDigest.digest(token)).expiresAt(expiresAt).createdAt(now).build();
        callbacks.persist(callback);
        return new TelegramIdentityService.TelegramCallbackToken(token, callback.getId());
    }

    Optional<TelegramIdentityService.MutationCallback> consume(String token, Instant now) {
        Optional<TelegramCallbackActionEntity> found = callbacks.findByDigestForUpdate(TelegramTokenDigest.digest(token));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        TelegramCallbackActionEntity callback = found.get();
        if (callback.getConsumedAt() != null || !callback.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        callback.setConsumedAt(now);
        return Optional.of(new TelegramIdentityService.MutationCallback(
            callback.getId(), callback.getFamilyId(), callback.getIdentityId(),
            callback.getAction(), callback.getTargetId()));
    }

    private static void requireFutureExpiry(Instant expiresAt, Instant now) {
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Telegram token expiry must be in the future");
        }
    }
}
