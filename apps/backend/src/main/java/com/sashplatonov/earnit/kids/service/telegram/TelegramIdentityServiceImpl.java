package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.TelegramCallbackActionEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramChildInvitationEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.repository.TelegramCallbackActionRepository;
import com.sashplatonov.earnit.kids.repository.TelegramChildInvitationRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.repository.TelegramSecurityAuditEventRepository;
import com.sashplatonov.earnit.kids.repository.TelegramWebhookUpdateRepository;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@ApplicationScoped
public class TelegramIdentityServiceImpl implements TelegramIdentityService {
    @Inject private TelegramIdentityRepository identities;
    @Inject private TelegramChildInvitationRepository invitations;
    @Inject private TelegramCallbackActionRepository callbacks;
    @Inject private TelegramWebhookUpdateRepository updates;
    @Inject private TelegramSecurityAuditEventRepository audits;
    @Inject private SecureTokenGenerator tokens;
    @Inject private com.sashplatonov.earnit.kids.service.family.ChildOwnershipService childOwnershipService;

    TelegramIdentityServiceImpl() {
    }

    TelegramIdentityServiceImpl(TelegramIdentityRepository identities,
                                TelegramChildInvitationRepository invitations,
                                TelegramCallbackActionRepository callbacks,
                                TelegramWebhookUpdateRepository updates,
                                TelegramSecurityAuditEventRepository audits,
                                SecureTokenGenerator tokens) {
        this.identities = identities;
        this.invitations = invitations;
        this.callbacks = callbacks;
        this.updates = updates;
        this.audits = audits;
        this.tokens = tokens;
    }

    @Override @Transactional
    public TelegramIdentity linkParent(Integer familyId, long telegramUserId, Integer parentAccountId, String actor, Instant now) {
        return linkParent(familyId, telegramUserId, parentAccountId, actor, null, null, now);
    }

    @Override @Transactional
    public TelegramIdentity linkParent(Integer familyId, long telegramUserId, Integer parentAccountId,
                                       String actor, String username, String displayName, Instant now) {
        Optional<TelegramIdentityEntity> existing = identities.findActiveByTelegramUserId(telegramUserId);
        if (existing.isPresent()) {
            TelegramIdentityEntity identity = existing.get();
            if (!"parent".equals(identity.getRole()) || !familyId.equals(identity.getFamilyId())) {
                throw new IllegalStateException("Telegram identity is already linked");
            }
            return identity(identity);
        }
        TelegramIdentityEntity created = TelegramIdentityEntity.builder()
            .familyId(familyId)
            .parentAccountId(parentAccountId)
            .telegramUserId(telegramUserId)
            .telegramUsername(username)
            .telegramDisplayName(displayName)
            .role("parent")
            .active(true)
            .linkedAt(now)
            .build();
        identities.persist(created);
        audit(familyId, null, created.getId(), "LINK", actor, now);
        return identity(created);
    }


    @Override
    public Optional<TelegramIdentity> findActiveByTelegramUserId(long telegramUserId) {
        return identities.findActiveByTelegramUserId(telegramUserId).map(TelegramIdentityServiceImpl::identity);
    }

    @Override @Transactional
    public boolean unlink(long telegramUserId, String actor, Instant now) {
        Optional<TelegramIdentityEntity> found = identities.findActiveByTelegramUserId(telegramUserId);
        if (found.isEmpty()) {
            return false;
        }
        TelegramIdentityEntity identity = found.get();
        identity.setActive(false); identity.setUnlinkedAt(now);
        audit(identity.getFamilyId(), identity.getChildId(), identity.getId(), "UNLINK", actor, now);
        return true;
    }

    @Override @Transactional
    public TelegramChildInvitationToken issueChildInvitation(Integer familyId, Integer childId, String issuedBy, Instant expiresAt, Instant now) {
        requireFutureExpiry(expiresAt, now);
        if (childOwnershipService.findFamilyChild(familyId, childId).isEmpty()) {
            throw new IllegalArgumentException("Child does not belong to family");
        }
        String token = tokens.generateHexToken(32);
        TelegramChildInvitationEntity invitation = TelegramChildInvitationEntity.builder()
            .familyId(familyId).childId(childId).secretDigest(digest(token)).expiresAt(expiresAt)
            .issuedBy(issuedBy).createdAt(now).build();
        invitations.persist(invitation);
        audit(familyId, childId, null, "INVITE_ISSUED", issuedBy, now);
        return new TelegramChildInvitationToken(token, invitation.getId());
    }

    @Override @Transactional
    public boolean revokeChildInvitation(Integer familyId, Integer invitationId, String actor, Instant now) {
        Optional<TelegramChildInvitationEntity> found = invitations.findByIdOptional(invitationId)
            .filter(i -> familyId.equals(i.getFamilyId()));
        if (found.isEmpty() || found.get().getRevokedAt() != null || found.get().getConsumedAt() != null) {
            return false;
        }
        found.get().setRevokedAt(now);
        audit(familyId, found.get().getChildId(), null, "INVITE_REVOKED", actor, now);
        return true;
    }

    @Override @Transactional
    public Optional<TelegramIdentity> acceptChildInvitation(String token, long telegramUserId, Instant now) {
        Optional<TelegramChildInvitationEntity> found = invitations.findByDigestForUpdate(digest(token));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        TelegramChildInvitationEntity invitation = found.get();
        if (invitation.getRevokedAt() != null || invitation.getConsumedAt() != null || !invitation.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        if (identities.findActiveByTelegramUserId(telegramUserId).isPresent()
            || identities.findActiveChild(invitation.getChildId()).isPresent()) {
            return Optional.empty();
        }
        TelegramIdentityEntity identity = TelegramIdentityEntity.builder()
            .familyId(invitation.getFamilyId()).childId(invitation.getChildId()).telegramUserId(telegramUserId)
            .role("child").active(true).linkedAt(now).build();
        identities.persist(identity);
        invitation.setConsumedAt(now);
        audit(invitation.getFamilyId(), invitation.getChildId(), identity.getId(), "INVITE_ACCEPTED", Long.toString(telegramUserId), now);
        return Optional.of(identity(identity));
    }

    @Override @Transactional
    public TelegramCallbackToken createMutationCallback(Integer familyId, Integer identityId, String action, long targetId, Instant expiresAt, Instant now) {
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
            .secretDigest(digest(token)).expiresAt(expiresAt).createdAt(now).build();
        callbacks.persist(callback);
        return new TelegramCallbackToken(token, callback.getId());
    }

    @Override @Transactional
    public Optional<MutationCallback> consumeMutationCallback(String token, Instant now) {
        Optional<TelegramCallbackActionEntity> found = callbacks.findByDigestForUpdate(digest(token));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        TelegramCallbackActionEntity callback = found.get();
        if (callback.getConsumedAt() != null || !callback.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        callback.setConsumedAt(now);
        return Optional.of(new MutationCallback(
            callback.getId(), callback.getFamilyId(), callback.getIdentityId(), callback.getAction(), callback.getTargetId()));
    }

    @Override @Transactional
    public boolean recordWebhookUpdate(long updateId, Instant now) {
        return updates.recordIfNew(updateId, now);
    }

    @Override
    public boolean needsReplyKeyboardReset(long telegramUserId, int configuredVersion) {
        return identities.findActiveByTelegramUserId(telegramUserId)
            .map(identity -> identity.getKeyboardVersion() < configuredVersion)
            .orElse(false);
    }

    @Override @Transactional
    public void markReplyKeyboardVersion(long telegramUserId, int version) {
        identities.findActiveByTelegramUserId(telegramUserId).ifPresent(identity -> {
            identity.setKeyboardVersion(version);
        });
    }

    private void audit(Integer familyId, Integer childId, Integer identityId, String type, String actor, Instant now) {
        audits.persist(com.sashplatonov.earnit.kids.domain.model.TelegramSecurityAuditEventEntity.builder()
            .familyId(familyId).childId(childId).identityId(identityId).eventType(type).actorReference(actor).createdAt(now).build());
    }

    private static TelegramIdentity identity(TelegramIdentityEntity identity) {
        return new TelegramIdentity(
            identity.getId(), identity.getFamilyId(), identity.getChildId(), identity.getTelegramUserId(),
            identity.getRole(), identity.getParentAccountId());
    }

    private static void requireFutureExpiry(Instant expiresAt, Instant now) {
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Telegram token expiry must be in the future");
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
