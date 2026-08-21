package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.TelegramChildInvitationEntity;
import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.repository.TelegramChildInvitationRepository;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.repository.TelegramSecurityAuditEventRepository;
import com.sashplatonov.earnit.kids.service.family.ChildOwnershipService;
import com.sashplatonov.earnit.kids.util.SecureTokenGenerator;

import java.time.Instant;
import java.util.Optional;

final class TelegramChildInvitationOperations {
    private final TelegramIdentityRepository identities;
    private final TelegramChildInvitationRepository invitations;
    private final TelegramSecurityAuditEventRepository audits;
    private final SecureTokenGenerator tokens;
    private final ChildOwnershipService childOwnershipService;

    TelegramChildInvitationOperations(TelegramIdentityRepository identities,
                                      TelegramChildInvitationRepository invitations,
                                      TelegramSecurityAuditEventRepository audits,
                                      SecureTokenGenerator tokens,
                                      ChildOwnershipService childOwnershipService) {
        this.identities = identities;
        this.invitations = invitations;
        this.audits = audits;
        this.tokens = tokens;
        this.childOwnershipService = childOwnershipService;
    }

    TelegramIdentityService.TelegramChildInvitationToken issue(
        Integer familyId, Integer childId, String issuedBy, Instant expiresAt, Instant now) {
        requireFutureExpiry(expiresAt, now);
        if (childOwnershipService == null || childOwnershipService.findFamilyChild(familyId, childId).isEmpty()) {
            throw new IllegalArgumentException("Child does not belong to family");
        }
        String token = tokens.generateHexToken(32);
        TelegramChildInvitationEntity invitation = TelegramChildInvitationEntity.builder()
            .familyId(familyId).childId(childId).secretDigest(TelegramTokenDigest.digest(token))
            .expiresAt(expiresAt).issuedBy(issuedBy).createdAt(now).build();
        invitations.persist(invitation);
        audit(familyId, childId, null, "INVITE_ISSUED", issuedBy, now);
        return new TelegramIdentityService.TelegramChildInvitationToken(token, invitation.getId());
    }

    boolean revoke(Integer familyId, Integer invitationId, String actor, Instant now) {
        Optional<TelegramChildInvitationEntity> found = invitations.findByIdOptional(invitationId)
            .filter(invitation -> familyId.equals(invitation.getFamilyId()));
        if (found.isEmpty() || found.get().getRevokedAt() != null || found.get().getConsumedAt() != null) {
            return false;
        }
        found.get().setRevokedAt(now);
        audit(familyId, found.get().getChildId(), null, "INVITE_REVOKED", actor, now);
        return true;
    }

    Optional<TelegramIdentityService.TelegramIdentity> accept(String token, long telegramUserId, Instant now) {
        Optional<TelegramChildInvitationEntity> found = invitations.findByDigestForUpdate(
            TelegramTokenDigest.digest(token));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        TelegramChildInvitationEntity invitation = found.get();
        if (invitation.getRevokedAt() != null || invitation.getConsumedAt() != null
            || !invitation.getExpiresAt().isAfter(now)) {
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
        audit(invitation.getFamilyId(), invitation.getChildId(), identity.getId(),
            "INVITE_ACCEPTED", Long.toString(telegramUserId), now);
        return Optional.of(toIdentity(identity));
    }

    private void audit(Integer familyId, Integer childId, Integer identityId, String type, String actor, Instant now) {
        TelegramSecurityAuditWriter.persist(audits, familyId, childId, identityId, type, actor, now);
    }

    private static TelegramIdentityService.TelegramIdentity toIdentity(TelegramIdentityEntity identity) {
        return new TelegramIdentityService.TelegramIdentity(
            identity.getId(), identity.getFamilyId(), identity.getChildId(), identity.getTelegramUserId(),
            identity.getRole(), identity.getParentAccountId());
    }

    private static void requireFutureExpiry(Instant expiresAt, Instant now) {
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Telegram token expiry must be in the future");
        }
    }
}
