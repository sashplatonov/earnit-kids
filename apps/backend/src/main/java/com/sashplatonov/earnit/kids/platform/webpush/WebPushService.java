package com.sashplatonov.earnit.kids.platform.webpush;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.family.application.notification.FamilyNotificationService;
import com.sashplatonov.earnit.kids.family.domain.model.outbox.*;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.outbox.ApplicationOutboxEventRepository;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.platform.security.SecurityAuditWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class WebPushService {
    private static final Set<ApplicationOutboxEventType> PARENT_EVENTS = Set.of(
        ApplicationOutboxEventType.TASK_REQUEST_CREATED, ApplicationOutboxEventType.REWARD_REQUEST_CREATED,
        ApplicationOutboxEventType.REQUEST_RESOLVED);
    private final WebPushSubscriptionRepository subscriptions;
    private final WebPushDeliveryRepository deliveries;
    private final FamilyRepository families;
    private final FamilyNotificationService preferences;
    private final SecurityAuditWriter audits;
    private final WebPushConfig config;
    private final WebPushProtocolAdapter protocol;
    private final ParentAccountRepository parentAccounts;
    private final FamilyParentMembershipRepository memberships;

    @Inject
    public WebPushService(WebPushSubscriptionRepository subscriptions, WebPushDeliveryRepository deliveries,
                          FamilyRepository families, FamilyNotificationService preferences,
                          SecurityAuditWriter audits, WebPushConfig config, WebPushProtocolAdapter protocol,
                          ParentAccountRepository parentAccounts, FamilyParentMembershipRepository memberships) {
        this.subscriptions = subscriptions; this.deliveries = deliveries; this.families = families;
        this.preferences = preferences; this.audits = audits; this.config = config;
        this.protocol = protocol; this.parentAccounts = parentAccounts; this.memberships = memberships;
    }

    // EXPLAIN: Keeps focused service tests independent from the protocol adapter.
    WebPushService(WebPushSubscriptionRepository subscriptions, WebPushDeliveryRepository deliveries,
                   FamilyRepository families, FamilyNotificationService preferences,
                   SecurityAuditWriter audits, WebPushConfig config) {
        this(subscriptions, deliveries, families, preferences, audits, config,
            new WebPushJavaAdapter(config), null, null);
    }

    public Optional<String> publicVapidKey() {
        if (!config.enabled()) return Optional.empty();
        if (config.vapidPrivateKey().filter(key -> !key.isBlank()).isEmpty()
            || config.vapidSubject().filter(key -> !key.isBlank()).isEmpty()) return Optional.empty();
        return config.vapidPublicKey().filter(key -> !key.isBlank());
    }

    @Transactional
    public void register(AuthContext auth, WebPushSubscriptionRequest request) {
        int familyId = families.getDbId(auth.familyId()).orElseThrow(() -> new IllegalArgumentException("family"));
        validate(request);
        String actorType = auth.isChild() ? "child" : "parent";
        Integer childId = auth.isChild() ? auth.childId() : null;
        if (auth.isChild() && childId == null) throw new SecurityException("actor");
        Integer parentId = auth.isChild() ? null : resolveParentAccountId(auth, familyId);
        if (!auth.isChild() && parentId == null) throw new SecurityException("actor");
        WebPushSubscriptionEntity entity = subscriptions.findByEndpoint(request.endpoint()).orElse(null);
        if (entity == null) {
            entity = WebPushSubscriptionEntity.builder().familyId(familyId).endpoint(request.endpoint())
                .p256dhKey(request.p256dh()).authKey(request.auth()).actorType(actorType)
                .parentAccountId(parentId).childId(childId).createdAt(Instant.now()).updatedAt(Instant.now()).build();
            subscriptions.persist(entity);
        } else {
            entity.setFamilyId(familyId); entity.setActorType(actorType); entity.setParentAccountId(parentId);
            entity.setChildId(childId); entity.setP256dhKey(request.p256dh()); entity.setAuthKey(request.auth());
            entity.setUpdatedAt(Instant.now());
        }
        audits.write(familyId, parentId, auth.email(), null, "web_push_subscription", "REGISTERED");
    }

    private Integer resolveParentAccountId(AuthContext auth, int familyDbId) {
        return Optional.ofNullable(auth.parentAccountId())
            .map(id -> memberships.findByParentAndFamily(id, familyDbId))
            .orElseGet(() -> Optional.ofNullable(auth.email())
                .filter(email -> !email.isBlank())
                .flatMap(parentAccounts::findByEmail)
                .flatMap(parent -> memberships.findByParentAndFamily(parent.getId(), familyDbId)))
            .map(membership -> membership.getParentAccountId())
            .orElse(null);
    }

    @Transactional
    public void unregister(AuthContext auth, WebPushSubscriptionRequest request) {
        int familyId = families.getDbId(auth.familyId()).orElseThrow(() -> new IllegalArgumentException("family"));
        validate(request);
        Integer parentId = auth.isChild() ? null : resolveParentAccountId(auth, familyId);
        subscriptions.deleteForActor(request.endpoint(), familyId, auth.isChild() ? "child" : "parent",
            parentId, auth.isChild() ? auth.childId() : null);
        audits.write(familyId, parentId, auth.email(), null, "web_push_subscription", "REMOVED");
    }

    @Transactional
    public int planDueEvents(Instant now, ApplicationOutboxEventRepository events) {
        int planned = 0;
        for (ApplicationOutboxEventEntity event : events.findPlanningCandidates(now.minus(Duration.ofMinutes(2)))) {
            boolean parent = PARENT_EVENTS.contains(event.getEventType());
            String key = preferenceKey(event.getEventType(), parent);
            boolean enabled = preferences.isEnabled(event.getFamilyId(), parent ? "parent" : "child", key,
                parent ? null : event.getChildId());
            List<WebPushSubscriptionEntity> recipients = parent ? subscriptions.findParents(event.getFamilyId())
                : subscriptions.findChild(event.getFamilyId(), event.getChildId());
            for (WebPushSubscriptionEntity subscription : recipients) {
                if (deliveries.findByEventAndSubscription(event.getId(), subscription.getId()).isEmpty()) {
                    deliveries.persist(WebPushDeliveryEntity.builder().eventId(event.getId()).subscriptionId(subscription.getId())
                        .transport("WEB_PUSH").title("EarnIt Kids").body(event.getEventType().name().replace('_', ' '))
                        .deepLink("/workspace").status(enabled && config.enabled() ? "PENDING" : "SKIPPED_DISABLED")
                        .nextAttemptAt(now).terminalAt(enabled && config.enabled() ? null : now).build());
                    planned++;
                }
            }
        }
        return planned;
    }

    public void send(WebPushSubscriptionEntity subscription, WebPushDeliveryEntity delivery) throws Exception {
        protocol.send(subscription, payload(delivery));
    }

    private String preferenceKey(ApplicationOutboxEventType type, boolean parent) {
        if (parent) return type == ApplicationOutboxEventType.REQUEST_RESOLVED ? "parentInviteAccepted" : "taskMarkedDone";
        return switch (type) { case TASK_APPROVED -> "taskApproved"; case TASK_REJECTED -> "taskRejected";
            case REWARD_APPROVED -> "rewardApproved"; case REWARD_REJECTED -> "rewardRejected";
            case REWARD_PURCHASED -> "rewardAvailable"; default -> "newTasks"; };
    }
    private void validate(WebPushSubscriptionRequest r) {
        if (r == null || r.endpoint() == null || !r.endpoint().startsWith("https://") || r.endpoint().length() > 4096
            || r.p256dh() == null || r.p256dh().length() > 200 || r.auth() == null || r.auth().length() > 100)
            throw new IllegalArgumentException("invalid push subscription");
    }
    private String payload(WebPushDeliveryEntity delivery) {
        return "{\"title\":\"" + escape(delivery.getTitle()) + "\",\"body\":\"" + escape(delivery.getBody())
            + "\",\"url\":\"" + escape(delivery.getDeepLink()) + "\"}";
    }
    private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
