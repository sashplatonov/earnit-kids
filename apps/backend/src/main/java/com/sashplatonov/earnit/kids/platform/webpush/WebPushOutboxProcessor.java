package com.sashplatonov.earnit.kids.platform.webpush;

import com.sashplatonov.earnit.kids.family.domain.model.outbox.ApplicationOutboxEventEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.outbox.ApplicationOutboxEventRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;

@ApplicationScoped
public class WebPushOutboxProcessor {
  private final WebPushService service;
  private final WebPushDeliveryRepository deliveries;
  private final WebPushSubscriptionRepository subscriptions;
  private final ApplicationOutboxEventRepository events;
  private final WebPushConfig config;

  @Inject
  public WebPushOutboxProcessor(
      WebPushService service,
      WebPushDeliveryRepository deliveries,
      WebPushSubscriptionRepository subscriptions,
      ApplicationOutboxEventRepository events,
      WebPushConfig config) {
    this.service = service;
    this.deliveries = deliveries;
    this.subscriptions = subscriptions;
    this.events = events;
    this.config = config;
  }

  @Scheduled(every = "10s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void scheduled() {
    if (config.enabled()) {
      process(Instant.now());
    }
  }

  @Transactional
  public int process(Instant now) {
    service.planDueEvents(now, events);
    int sent = 0;
    for (WebPushDeliveryEntity delivery : deliveries.findDue(now, now.minusSeconds(60))) {
      delivery.setClaimedAt(now);
      WebPushSubscriptionEntity subscription = subscriptions.findById(delivery.getSubscriptionId());
      if (subscription == null) {
        terminal(delivery, "FAILED", now);
        continue;
      }
      try {
        service.send(subscription, delivery);
        terminal(delivery, "SENT", now);
        sent++;
      } catch (WebPushTransportException failure) {
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setLastError("HTTP_" + failure.status());
        if (failure.status() == 404 || failure.status() == 410) {
          subscriptions.deleteById(subscription.getId());
          terminal(delivery, "FAILED", now);
        } else if (delivery.getAttemptCount() >= config.maxAttempts()) {
          terminal(delivery, "FAILED", now);
        } else {
          retry(delivery, now);
        }
      } catch (Exception failure) {
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setLastError(failure.getClass().getSimpleName());
        if (delivery.getAttemptCount() >= config.maxAttempts()) {
          terminal(delivery, "FAILED", now);
        } else {
          retry(delivery, now);
        }
      }
      ApplicationOutboxEventEntity event = events.findById(delivery.getEventId());
      if (event != null && events.allTransportsTerminal(event.getId())) {
        event.setPlanningCompletedAt(now);
        event.setPlanningStatus("COMPLETE");
      }
    }
    return sent;
  }

  private void retry(WebPushDeliveryEntity d, Instant now) {
    d.setClaimedAt(null);
    d.setNextAttemptAt(now.plusSeconds(1L << Math.min(6, d.getAttemptCount())));
  }

  private void terminal(WebPushDeliveryEntity d, String status, Instant now) {
    d.setStatus(status);
    d.setTerminalAt(now);
    d.setClaimedAt(null);
  }
}
