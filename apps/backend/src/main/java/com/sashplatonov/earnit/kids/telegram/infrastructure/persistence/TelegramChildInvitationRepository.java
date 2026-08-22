package com.sashplatonov.earnit.kids.telegram.infrastructure.persistence;

import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramChildInvitationEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.time.Instant;

@ApplicationScoped
public class TelegramChildInvitationRepository
    implements PanacheRepositoryBase<TelegramChildInvitationEntity, Integer> {
    public Optional<TelegramChildInvitationEntity> findByDigest(String digest) {
        return find("secretDigest = ?1", digest).firstResultOptional();
    }

    public Optional<TelegramChildInvitationEntity> findByDigestForUpdate(String digest) {
        return find("secretDigest = ?1", digest)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResultOptional();
    }

    public int deleteEligible(Instant cutoff, int batchSize) {
        var rows = find("expiresAt < ?1 or revokedAt < ?1 order by id", cutoff)
            .range(0, batchSize - 1).list();
        rows.forEach(this::delete);
        return rows.size();
    }

    public long countEligible(Instant cutoff) {
        return count("expiresAt < ?1 or revokedAt < ?1", cutoff);
    }
}
