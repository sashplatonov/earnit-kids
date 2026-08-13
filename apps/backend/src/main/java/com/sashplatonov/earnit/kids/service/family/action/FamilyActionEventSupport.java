package com.sashplatonov.earnit.kids.service.family.action;

import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;
import com.sashplatonov.earnit.kids.service.event.ApplicationEventPublisher;

import java.time.Instant;

final class FamilyActionEventSupport {
    private final ApplicationEventPublisher publisher;

    FamilyActionEventSupport(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    void publish(ApplicationOutboxEventType type, int familyId, int childId, Long requestId,
                 int delta, Integer balance, Instant at) {
        if (publisher != null) {
            publisher.publish(type, familyId, childId, requestId, delta, balance, at);
        }
    }
}
