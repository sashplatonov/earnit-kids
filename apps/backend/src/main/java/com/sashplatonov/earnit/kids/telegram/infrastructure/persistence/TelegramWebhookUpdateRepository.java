package com.sashplatonov.earnit.kids.telegram.infrastructure.persistence;

import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramWebhookUpdateEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;

@ApplicationScoped
public class TelegramWebhookUpdateRepository implements PanacheRepositoryBase<TelegramWebhookUpdateEntity, Integer> {
    @PersistenceContext
    EntityManager entityManager;
    @ConfigProperty(name = "DB_SCHEMA", defaultValue = "earnit_kids")
    String schema;

    public boolean recordIfNew(long updateId, Instant receivedAt) {
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("Database schema name is invalid");
        }
        String query = "MERGE INTO " + schema + ".telegram_webhook_updates AS target "
            + "USING (VALUES (?1, CAST(?2 AS TIMESTAMP WITH TIME ZONE))) AS source(update_id, received_at) "
            + "ON target.update_id = source.update_id "
            + "WHEN NOT MATCHED THEN INSERT (update_id, received_at) "
            + "VALUES (source.update_id, source.received_at)";
        return entityManager.createNativeQuery(query)
            .setParameter(1, updateId)
            .setParameter(2, receivedAt)
            .executeUpdate() == 1;
    }

    public int deleteEligible(Instant cutoff, int batchSize) {
        var rows = find("receivedAt < ?1 order by id", cutoff).range(0, batchSize - 1).list();
        rows.forEach(this::delete);
        return rows.size();
    }

    public long countEligible(Instant cutoff) {
        return count("receivedAt < ?1", cutoff);
    }
}
