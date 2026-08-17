package com.sashplatonov.earnit.kids.service.event;

import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.domain.model.RequestResolutionStatus;
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
        return publish(type, familyId, childId, requestId, delta, balance, createdAt, null, null);
    }

    public ApplicationOutboxEventEntity publish(ApplicationOutboxEventType type, int familyId, int childId,
                                                Long requestId, int delta, Integer balance, Instant createdAt,
                                                RequestResolutionStatus resolutionStatus, String resolutionTitle) {
        ApplicationOutboxEventEntity event = ApplicationOutboxEventEntity.builder()
            .eventType(type).familyId(familyId).childId(childId).requestId(requestId)
            .coinDelta(delta).resultingBalance(balance).createdAt(createdAt)
            .resolutionStatus(resolutionStatus).resolutionTitle(resolutionTitle)
            .planningStatus("UNPLANNED").build();
        events.persist(event);
        return event;
    }
}
