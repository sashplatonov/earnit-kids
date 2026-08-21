package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.repository.TelegramCallbackActionRepository;
import com.sashplatonov.earnit.kids.repository.TelegramChildInvitationRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.repository.TelegramSecurityAuditEventRepository;
import com.sashplatonov.earnit.kids.repository.TelegramWebhookUpdateRepository;
import com.sashplatonov.earnit.kids.service.family.ChildOwnershipService;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class TelegramIdentityServiceImpl implements TelegramIdentityService {
    private final TelegramIdentityRepository identities;
    private final TelegramSecurityAuditEventRepository audits;
    private final TelegramWebhookUpdateRepository updates;
    private final TelegramChildInvitationOperations invitationOperations;
    private final TelegramMutationCallbackOperations callbackOperations;

    @Inject
    TelegramIdentityServiceImpl(TelegramIdentityRepository identities,
                                TelegramChildInvitationRepository invitations,
                                TelegramCallbackActionRepository callbacks,
                                TelegramWebhookUpdateRepository updates,
                                TelegramSecurityAuditEventRepository audits,
                                SecureTokenGenerator tokens,
                                ChildOwnershipService childOwnershipService) {
        this.identities = identities;
        this.audits = audits;
        this.updates = updates;
        this.invitationOperations = new TelegramChildInvitationOperations(
            identities, invitations, audits, tokens, childOwnershipService);
        this.callbackOperations = new TelegramMutationCallbackOperations(identities, callbacks, tokens);
    }

    TelegramIdentityServiceImpl(TelegramIdentityRepository identities,
                                TelegramChildInvitationRepository invitations,
                                TelegramCallbackActionRepository callbacks,
                                TelegramWebhookUpdateRepository updates,
                                TelegramSecurityAuditEventRepository audits,
                                SecureTokenGenerator tokens) {
        this(identities, invitations, callbacks, updates, audits, tokens, null);
    }

    @Override
    @Transactional
    public TelegramIdentity linkParent(Integer familyId, long telegramUserId, Integer parentAccountId,
                                       String actor, Instant now) {
        return linkParent(familyId, telegramUserId, parentAccountId, actor, null, null, now);
    }

    @Override
    @Transactional
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
            .familyId(familyId).parentAccountId(parentAccountId).telegramUserId(telegramUserId)
            .telegramUsername(username).telegramDisplayName(displayName).role("parent")
            .active(true).linkedAt(now).build();
        identities.persist(created);
        audit(familyId, null, created.getId(), "LINK", actor, now);
        return identity(created);
    }

    @Override
    public Optional<TelegramIdentity> findActiveByTelegramUserId(long telegramUserId) {
        return identities.findActiveByTelegramUserId(telegramUserId).map(TelegramIdentityServiceImpl::identity);
    }

    @Override
    @Transactional
    public boolean unlink(long telegramUserId, String actor, Instant now) {
        Optional<TelegramIdentityEntity> found = identities.findActiveByTelegramUserId(telegramUserId);
        if (found.isEmpty()) {
            return false;
        }
        TelegramIdentityEntity identity = found.get();
        identity.setActive(false);
        identity.setUnlinkedAt(now);
        audit(identity.getFamilyId(), identity.getChildId(), identity.getId(), "UNLINK", actor, now);
        return true;
    }

    @Override
    @Transactional
    public TelegramChildInvitationToken issueChildInvitation(
        Integer familyId, Integer childId, String issuedBy, Instant expiresAt, Instant now) {
        return invitationOperations.issue(familyId, childId, issuedBy, expiresAt, now);
    }

    @Override
    @Transactional
    public boolean revokeChildInvitation(Integer familyId, Integer invitationId, String actor, Instant now) {
        return invitationOperations.revoke(familyId, invitationId, actor, now);
    }

    @Override
    @Transactional
    public Optional<TelegramIdentity> acceptChildInvitation(String token, long telegramUserId, Instant now) {
        return invitationOperations.accept(token, telegramUserId, now);
    }

    @Override
    @Transactional
    public TelegramCallbackToken createMutationCallback(
        Integer familyId, Integer identityId, String action, long targetId, Instant expiresAt, Instant now) {
        return callbackOperations.create(familyId, identityId, action, targetId, expiresAt, now);
    }

    @Override
    @Transactional
    public Optional<MutationCallback> consumeMutationCallback(String token, Instant now) {
        return callbackOperations.consume(token, now);
    }

    @Override
    @Transactional
    public boolean recordWebhookUpdate(long updateId, Instant now) {
        return updates.recordIfNew(updateId, now);
    }

    @Override
    public boolean needsReplyKeyboardReset(long telegramUserId, int configuredVersion) {
        return identities.findActiveByTelegramUserId(telegramUserId)
            .map(identity -> identity.getKeyboardVersion() < configuredVersion).orElse(false);
    }

    @Override
    @Transactional
    public void markReplyKeyboardVersion(long telegramUserId, int version) {
        identities.findActiveByTelegramUserId(telegramUserId)
            .ifPresent(identity -> identity.setKeyboardVersion(version));
    }

    private void audit(Integer familyId, Integer childId, Integer identityId, String type, String actor, Instant now) {
        TelegramSecurityAuditWriter.persist(audits, familyId, childId, identityId, type, actor, now);
    }

    private static TelegramIdentity identity(TelegramIdentityEntity identity) {
        return new TelegramIdentity(
            identity.getId(), identity.getFamilyId(), identity.getChildId(), identity.getTelegramUserId(),
            identity.getRole(), identity.getParentAccountId());
    }
}
