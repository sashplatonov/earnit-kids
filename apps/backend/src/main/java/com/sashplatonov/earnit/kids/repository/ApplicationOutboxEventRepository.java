package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ApplicationOutboxEventRepository implements PanacheRepositoryBase<ApplicationOutboxEventEntity, Long> {
    public List<ApplicationOutboxEventEntity> findPlanningCandidates(Instant now) {
        return find("planningCompletedAt is null and (planningClaimedAt is null or planningClaimedAt < ?1) order by id", now)
            .withLock(LockModeType.PESSIMISTIC_WRITE).range(0, 49).list();
    }
    public boolean hasDeliveryPlanning(Long eventId) {
        return findByIdOptional(eventId).map(e -> e.getPlanningCompletedAt() != null).orElse(false);
    }

    public int deleteEligible(Instant cutoff, int batchSize) {
        var rows = find("planningCompletedAt is not null and planningCompletedAt < ?1 "
                + "and id not in (select d.eventId from TelegramDeliveryEntity d "
                + "where d.status = 'PENDING') order by id",
                cutoff)
            .range(0, batchSize - 1).list();
        rows.forEach(this::delete);
        return rows.size();
    }

    public long countEligible(Instant cutoff) {
        return count("planningCompletedAt is not null and planningCompletedAt < ?1 "
                + "and id not in (select d.eventId from TelegramDeliveryEntity d where d.status = 'PENDING')",
                cutoff);
    }
}
