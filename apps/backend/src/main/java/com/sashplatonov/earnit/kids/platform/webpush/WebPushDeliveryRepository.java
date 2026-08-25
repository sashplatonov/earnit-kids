package com.sashplatonov.earnit.kids.platform.webpush;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class WebPushDeliveryRepository
    implements PanacheRepositoryBase<WebPushDeliveryEntity, Long> {
  public Optional<WebPushDeliveryEntity> findByEventAndSubscription(
      Long eventId, Long subscriptionId) {
    return find(
            "eventId = ?1 and subscriptionId = ?2 and transport = 'WEB_PUSH'",
            eventId,
            subscriptionId)
        .firstResultOptional();
  }

  public List<WebPushDeliveryEntity> findDue(Instant now, Instant expired) {
    return find(
            "status = 'PENDING' and nextAttemptAt <= ?1 and (claimedAt is null or claimedAt < ?2) order by id",
            now,
            expired)
        .withLock(LockModeType.PESSIMISTIC_WRITE)
        .range(0, 49)
        .list();
  }

  public List<WebPushDeliveryEntity> findByEvent(Long eventId) {
    return find("eventId = ?1", eventId).list();
  }
}
