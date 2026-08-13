package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.TelegramCallbackActionEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.time.Instant;

@ApplicationScoped
public class TelegramCallbackActionRepository implements PanacheRepositoryBase<TelegramCallbackActionEntity, Integer> {
    public Optional<TelegramCallbackActionEntity> findByDigest(String digest) {
        return find("secretDigest = ?1", digest).firstResultOptional();
    }

    public Optional<TelegramCallbackActionEntity> findByDigestForUpdate(String digest) {
        return find("secretDigest = ?1", digest)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResultOptional();
    }

    public int deleteEligible(Instant cutoff, int batchSize) {
        var rows = find("(consumedAt is not null and consumedAt < ?1) or expiresAt < ?1 order by id", cutoff)
            .range(0, batchSize - 1).list();
        rows.forEach(this::delete);
        return rows.size();
    }

    public long countEligible(Instant cutoff) {
        return count("(consumedAt is not null and consumedAt < ?1) or expiresAt < ?1", cutoff);
    }
}
