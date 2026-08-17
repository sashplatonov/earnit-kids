package com.sashplatonov.earnit.kids.service.telegram;

import java.time.Instant;
import java.util.Optional;

public interface TelegramIdentityService {
    Optional<TelegramIdentity> findActiveByTelegramUserId(long telegramUserId);
    TelegramIdentity linkParent(Integer familyId, long telegramUserId, Integer parentAccountId, String actor, Instant now);
    boolean unlink(long telegramUserId, String actor, Instant now);
    TelegramChildInvitationToken issueChildInvitation(Integer familyId, Integer childId, String issuedBy, Instant expiresAt, Instant now);
    boolean revokeChildInvitation(Integer familyId, Integer invitationId, String actor, Instant now);
    Optional<TelegramIdentity> acceptChildInvitation(String token, long telegramUserId, Instant now);
    TelegramCallbackToken createMutationCallback(Integer familyId, Integer identityId, String action, long targetId, Instant expiresAt, Instant now);
    Optional<MutationCallback> consumeMutationCallback(String token, Instant now);
    boolean recordWebhookUpdate(long updateId, Instant now);

    // EXPLAIN: UX-01 — returns true when the identity's stored reply-keyboard
    // EXPLAIN: version is behind the configured version, meaning the client may
    // EXPLAIN: still hold a stale cached keyboard and needs a one-time reset.
    boolean needsReplyKeyboardReset(long telegramUserId, int configuredVersion);

    // EXPLAIN: UX-01 — records that the identity has been shown the current
    // EXPLAIN: reply-keyboard version, so the reset is only sent once per version.
    void markReplyKeyboardVersion(long telegramUserId, int version);

    record TelegramIdentity(Integer id, Integer familyId, Integer childId, long telegramUserId, String role) { }

    record TelegramChildInvitationToken(String token, Integer invitationId) { }

    record TelegramCallbackToken(String token, Integer callbackId) { }

    record MutationCallback(Integer id, Integer familyId, Integer identityId, String action, long targetId) { }
}
