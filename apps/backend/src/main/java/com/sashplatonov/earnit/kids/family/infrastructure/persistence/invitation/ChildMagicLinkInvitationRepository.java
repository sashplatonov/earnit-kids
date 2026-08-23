package com.sashplatonov.earnit.kids.family.infrastructure.persistence.invitation;

import com.sashplatonov.earnit.kids.family.domain.model.invitation.ChildMagicLinkInvitationEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ChildMagicLinkInvitationRepository
    implements PanacheRepositoryBase<ChildMagicLinkInvitationEntity, Integer> {

    public Optional<ChildMagicLinkInvitationEntity> findPendingByChild(Integer familyId, Integer childId) {
        return find("familyId = ?1 AND childId = ?2 AND status = 'pending' AND expiresAt > ?3",
            familyId, childId, Instant.now()).firstResultOptional();
    }

    public Optional<ChildMagicLinkInvitationEntity> findByDigest(String digest) {
        return find("tokenDigest = ?1", digest).firstResultOptional();
    }

    public List<ChildMagicLinkInvitationEntity> findByChild(Integer familyId, Integer childId) {
        return find("familyId = ?1 AND childId = ?2 ORDER BY createdAt DESC", familyId, childId).list();
    }

    @Transactional
    public int revokePending(Integer familyId, Integer childId, Instant now) {
        return update("status = 'revoked', revokedAt = ?1 "
            + "WHERE familyId = ?2 AND childId = ?3 AND status = 'pending'",
            now, familyId, childId);
    }

    @Transactional
    public int revoke(Integer familyId, Integer childId, Instant now) {
        return update("status = 'revoked', revokedAt = ?1 "
            + "WHERE familyId = ?2 AND childId = ?3 AND status = 'pending'",
            now, familyId, childId);
    }

    @Transactional
    public int consume(Integer id, Instant now) {
        return update("status = 'consumed', consumedAt = ?1 "
            + "WHERE id = ?2 AND status = 'pending' AND expiresAt > ?1",
            now, id);
    }
}
