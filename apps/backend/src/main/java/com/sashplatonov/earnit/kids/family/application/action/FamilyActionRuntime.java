package com.sashplatonov.earnit.kids.family.application.action;

import com.sashplatonov.earnit.kids.service.event.ApplicationEventPublisher;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public final class FamilyActionRuntime {
  private final TimeProvider timeProvider;
  private final ApplicationEventPublisher eventPublisher;

  @Inject
  public FamilyActionRuntime(TimeProvider timeProvider, ApplicationEventPublisher eventPublisher) {
    this.timeProvider = timeProvider;
    this.eventPublisher = eventPublisher;
  }

  TimeProvider timeProvider() {
    return timeProvider;
  }

  ApplicationEventPublisher eventPublisher() {
    return eventPublisher;
  }
}
