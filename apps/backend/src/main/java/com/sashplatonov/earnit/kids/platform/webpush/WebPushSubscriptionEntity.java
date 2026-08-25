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
@Table(name = "web_push_subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class WebPushSubscriptionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "family_id", nullable = false)
  private Integer familyId;

  @Column(name = "parent_account_id")
  private Integer parentAccountId;

  @Column(name = "child_id")
  private Integer childId;

  @Column(name = "actor_type", nullable = false)
  private String actorType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String endpoint;

  @Column(name = "p256dh_key", nullable = false)
  private String p256dhKey;

  @Column(name = "auth_key", nullable = false)
  private String authKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
