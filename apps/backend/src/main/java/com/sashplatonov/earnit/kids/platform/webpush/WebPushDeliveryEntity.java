package com.sashplatonov.earnit.kids.platform.webpush;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "web_push_deliveries")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class WebPushDeliveryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false)
  private Long eventId;

  @Column(name = "subscription_id", nullable = false)
  private Long subscriptionId;

  @Column(nullable = false)
  private String transport;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, length = 500)
  private String body;

  @Column(name = "deep_link", nullable = false, length = 500)
  private String deepLink;

  @Column(nullable = false)
  private String status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "terminal_at")
  private Instant terminalAt;

  @Column(name = "last_error")
  private String lastError;
}
