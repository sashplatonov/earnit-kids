package com.sashplatonov.earnit.kids.platform.webpush;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "web_push_subscriptions")
@Getter @Setter @Builder @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class WebPushSubscriptionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "family_id", nullable = false) private Integer familyId;
    @Column(name = "parent_account_id") private Integer parentAccountId;
    @Column(name = "child_id") private Integer childId;
    @Column(name = "actor_type", nullable = false) private String actorType;
    @Column(nullable = false, columnDefinition = "TEXT") private String endpoint;
    @Column(name = "p256dh_key", nullable = false) private String p256dhKey;
    @Column(name = "auth_key", nullable = false) private String authKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
