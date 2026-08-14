package com.sashplatonov.earnit.kids.service.event;

import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.repository.ApplicationOutboxEventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class ApplicationEventPublisher {
    @Inject
    private ApplicationOutboxEventRepository events;

    ApplicationEventPublisher() {
    }

    ApplicationEventPublisher(ApplicationOutboxEventRepository events) {
        this.events = events;
    }

    public ApplicationOutboxEventEntity publish(ApplicationOutboxEventType type, int familyId, int childId,
                                                Long requestId, int delta, Integer balance, Instant createdAt) {
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .eventType(type).familyId(familyId).childId(childId).requestId(requestId)
            .coinDelta(delta).resultingBalance(balance).createdAt(createdAt)
            .planningStatus("UNPLANNED").build();
        events.persist(event);
        return event;
    }
}
