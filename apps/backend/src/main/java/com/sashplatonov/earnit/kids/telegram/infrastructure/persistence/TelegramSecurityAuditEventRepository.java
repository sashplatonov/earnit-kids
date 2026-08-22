package com.sashplatonov.earnit.kids.telegram.infrastructure.persistence;

import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramSecurityAuditEventEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class TelegramSecurityAuditEventRepository
    implements PanacheRepositoryBase<TelegramSecurityAuditEventEntity, Integer> {
    public int deleteEligible(Instant cutoff, int batchSize) {
        var rows = find("createdAt < ?1 order by id", cutoff).range(0, batchSize - 1).list();
        rows.forEach(this::delete);
        return rows.size();
    }

    public long countEligible(Instant cutoff) {
        return count("createdAt < ?1", cutoff);
    }
}
