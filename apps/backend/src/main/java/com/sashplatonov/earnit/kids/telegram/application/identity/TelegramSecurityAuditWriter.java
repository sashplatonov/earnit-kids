package com.sashplatonov.earnit.kids.telegram.application.identity;

import com.sashplatonov.earnit.kids.telegram.domain.model.TelegramSecurityAuditEventEntity;
import com.sashplatonov.earnit.kids.telegram.infrastructure.persistence.TelegramSecurityAuditEventRepository;

import java.time.Instant;

public final class TelegramSecurityAuditWriter {

    private TelegramSecurityAuditWriter() {
    }

    public static void persist(TelegramSecurityAuditEventRepository audits,
                        Integer familyId,
                        Integer childId,
                        Integer identityId,
                        String type,
                        String actor,
                        Instant now) {
        audits.persist(TelegramSecurityAuditEventEntity.builder()
            .familyId(familyId).childId(childId).identityId(identityId)
            .eventType(type).actorReference(actor).createdAt(now).build());
    }
}
