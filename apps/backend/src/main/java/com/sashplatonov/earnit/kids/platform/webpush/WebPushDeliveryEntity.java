package com.sashplatonov.earnit.kids.platform.webpush;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "web_push_deliveries")
@Getter @Setter @Builder @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class WebPushDeliveryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false) private Long eventId;
    @Column(name = "subscription_id", nullable = false) private Long subscriptionId;
    @Column(nullable = false) private String transport;
    @Column(nullable = false) private String title;
    @Column(nullable = false, length = 500) private String body;
    @Column(name = "deep_link", nullable = false, length = 500) private String deepLink;
    @Column(nullable = false) private String status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "terminal_at") private Instant terminalAt;
    @Column(name = "last_error") private String lastError;
}
