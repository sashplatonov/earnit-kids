package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.TelegramSecurityAuditEventEntity;
import com.sashplatonov.earnit.kids.repository.TelegramSecurityAuditEventRepository;

import java.time.Instant;

final class TelegramSecurityAuditWriter {

    private TelegramSecurityAuditWriter() {
    }

    static void persist(TelegramSecurityAuditEventRepository audits,
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
