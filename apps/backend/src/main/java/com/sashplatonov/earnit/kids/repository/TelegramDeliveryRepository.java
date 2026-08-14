package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.TelegramDeliveryEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TelegramDeliveryRepository implements PanacheRepositoryBase<TelegramDeliveryEntity, Long> {
    public Optional<TelegramDeliveryEntity> findByEventAndRecipient(Long eventId, Integer identityId) {
        return find("eventId = ?1 and recipientIdentityId = ?2", eventId, identityId).firstResultOptional();
    }
    public List<TelegramDeliveryEntity> findDue(Instant now, Instant expiredClaimBefore) {
        return find(
            "status = 'PENDING' and nextAttemptAt <= ?1 and (claimedAt is null or claimedAt < ?2) order by id",
            now, expiredClaimBefore
        )
            .withLock(LockModeType.PESSIMISTIC_WRITE).range(0, 49).list();
    }
    public List<TelegramDeliveryEntity> findByEvent(Long eventId) {
        return find("eventId = ?1 order by id", eventId).list();
    }

    public int deleteEligible(Instant cutoff, int batchSize) {
        var rows = find("terminalAt is not null and terminalAt < ?1 "
                + "and status in ('SENT', 'SKIPPED', 'SKIPPED_DISABLED', 'FAILED') order by id", cutoff)
            .range(0, batchSize - 1).list();
        rows.forEach(this::delete);
        return rows.size();
    }

    public long countEligible(Instant cutoff) {
        return count("terminalAt is not null and terminalAt < ?1 "
                + "and status in ('SENT', 'SKIPPED', 'SKIPPED_DISABLED', 'FAILED')", cutoff);
    }
}
