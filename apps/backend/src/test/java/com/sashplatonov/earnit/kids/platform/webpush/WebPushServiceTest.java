package com.sashplatonov.earnit.kids.platform.webpush;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.family.application.notification.FamilyNotificationService;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.platform.security.SecurityAuditWriter;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class WebPushServiceTest {
    private final WebPushSubscriptionRepository subscriptions = mock(WebPushSubscriptionRepository.class);
    private final WebPushDeliveryRepository deliveries = mock(WebPushDeliveryRepository.class);
    private final FamilyRepository families = mock(FamilyRepository.class);
    private final FamilyNotificationService preferences = mock(FamilyNotificationService.class);
    private final SecurityAuditWriter audits = mock(SecurityAuditWriter.class);
    private final WebPushConfig config = mock(WebPushConfig.class);
    private final WebPushService service = new WebPushService(subscriptions, deliveries, families,
        preferences, audits, config);

    @Test
    void registrationUsesAuthenticatedParentAndNeverClientIdentity() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(7));
        when(subscriptions.findByEndpoint("https://push.example/sub")).thenReturn(Optional.empty());
        AuthContext auth = new AuthContext("family-1", null, "admin", "parent@example.com", "csrf",
            "family_admin", 42);

        service.register(auth, new WebPushSubscriptionRequest("https://push.example/sub", "public-key", "auth"));

        ArgumentCaptor<WebPushSubscriptionEntity> captor = ArgumentCaptor.forClass(WebPushSubscriptionEntity.class);
        verify(subscriptions).persist(captor.capture());
        WebPushSubscriptionEntity subscription = captor.getValue();
        assertThat(subscription.getFamilyId()).isEqualTo(7);
        assertThat(subscription.getParentAccountId()).isEqualTo(42);
        assertThat(subscription.getActorType()).isEqualTo("parent");
        verify(audits).write(7, 42, "parent@example.com", null, "web_push_subscription", "REGISTERED");
    }

    @Test
    void malformedEndpointIsRejectedBeforePersistence() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(7));
        AuthContext auth = new AuthContext("family-1", 3, "child", "child@example.com", "csrf", "child");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.register(auth,
            new WebPushSubscriptionRequest("http://not-push", "key", "auth")))
            .isInstanceOf(IllegalArgumentException.class);
        verify(subscriptions, never()).persist(any(WebPushSubscriptionEntity.class));
    }

    @Test
    void parentWithoutParentAccountIdIsAccepted() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(7));
        when(subscriptions.findByEndpoint("https://push.example/sub")).thenReturn(Optional.empty());
        // EXPLAIN: A web (non-Telegram) parent session may not carry parentAccountId
        // EXPLAIN: in the auth cookie; registration must not 403 for this case.
        AuthContext auth = new AuthContext("family-1", null, "admin", "parent@example.com", "csrf",
            "family_admin", null);

        service.register(auth, new WebPushSubscriptionRequest("https://push.example/sub", "public-key", "auth"));

        ArgumentCaptor<WebPushSubscriptionEntity> captor = ArgumentCaptor.forClass(WebPushSubscriptionEntity.class);
        verify(subscriptions).persist(captor.capture());
        WebPushSubscriptionEntity subscription = captor.getValue();
        assertThat(subscription.getFamilyId()).isEqualTo(7);
        assertThat(subscription.getParentAccountId()).isNull();
        assertThat(subscription.getActorType()).isEqualTo("parent");
    }
}
