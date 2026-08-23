package com.sashplatonov.earnit.kids.family.infrastructure.persistence.invitation;

import com.sashplatonov.earnit.kids.family.domain.model.invitation.ParentEmailInvitationEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

@ApplicationScoped
public class ParentEmailInvitationRepository
    implements PanacheRepositoryBase<ParentEmailInvitationEntity, Integer> {

    public Optional<ParentEmailInvitationEntity> findPending(Integer familyId, String normalizedEmail) {
        return find("familyId = ?1 AND normalizedEmail = ?2 AND status = 'pending' "
                + "AND expiresAt > ?3", familyId, normalizedEmail, Instant.now())
            .firstResultOptional();
    }

    public Optional<ParentEmailInvitationEntity> findByDigest(String tokenDigest, String keyId) {
        return find("tokenDigest = ?1 AND tokenDigestKeyId = ?2", tokenDigest, keyId).firstResultOptional();
    }

    public List<ParentEmailInvitationEntity> findPendingByFamily(Integer familyId) {
        return find("familyId = ?1 AND status = 'pending' AND expiresAt > ?2",
            familyId, Instant.now()).list();
    }

    public int consume(Integer id, Instant now) {
        return update("status = 'accepted', consumedAt = ?1 "
            + "WHERE id = ?2 AND status = 'pending' AND revokedAt IS NULL AND supersededAt IS NULL "
            + "AND expiresAt > ?1", now, id);
    }

    public int revoke(Integer id, Instant now) {
        return update("status = 'revoked', revokedAt = ?1 "
            + "WHERE id = ?2 AND status = 'pending'", now, id);
    }

    public int revokeForFamily(Integer id, Integer familyId, Instant now) {
        return update("status = 'revoked', revokedAt = ?1 "
            + "WHERE id = ?2 AND familyId = ?3 AND status = 'pending'", now, id, familyId);
    }

    public int rotate(Integer id, Integer familyId, String tokenDigest, String keyId,
                      Instant expiresAt, Instant now) {
        return update("tokenDigest = ?1, expiresAt = ?2 "
                + ", tokenDigestKeyId = ?3 "
                + "WHERE id = ?4 AND familyId = ?5 AND status = 'pending' AND expiresAt > ?6",
            tokenDigest, expiresAt, keyId, id, familyId, now);
    }
}
