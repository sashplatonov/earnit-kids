package com.sashplatonov.earnit.kids.platform.webpush;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.family.application.notification.FamilyNotificationService;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.identity.domain.model.ParentAccountEntity;
import com.sashplatonov.earnit.kids.identity.infrastructure.persistence.ParentAccountRepository;
import com.sashplatonov.earnit.kids.platform.security.SecurityAuditWriter;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private final ParentAccountRepository parentAccounts = mock(ParentAccountRepository.class);
    private final FamilyParentMembershipRepository memberships = mock(FamilyParentMembershipRepository.class);
    private final WebPushService service = new WebPushService(subscriptions, deliveries, families,
        preferences, audits, config, new WebPushJavaAdapter(config), parentAccounts, memberships);

    @Test
    void registrationUsesAuthenticatedParentAndNeverClientIdentity() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(7));
        when(subscriptions.findByEndpoint("https://push.example/sub")).thenReturn(Optional.empty());
        AuthContext auth = new AuthContext("family-1", null, "admin", "parent@example.com", "csrf",
            "family_admin", 42);
        FamilyParentMembershipEntity membership = membership(42);
        when(memberships.findByParentAndFamily(42, 7)).thenReturn(Optional.of(membership));

        service.register(auth, new WebPushSubscriptionRequest("https://push.example/sub", "public-key", "auth"));

        ArgumentCaptor<WebPushSubscriptionEntity> captor = ArgumentCaptor.forClass(WebPushSubscriptionEntity.class);
        verify(subscriptions).persist(captor.capture());
        WebPushSubscriptionEntity subscription = captor.getValue();
        assertThat(subscription.getFamilyId()).isEqualTo(7);
        assertThat(subscription.getParentAccountId()).isEqualTo(42);
        assertThat(subscription.getActorType()).isEqualTo("parent");
        verify(audits).write(7, 42, "parent@example.com", null, "web_push_subscription", "REGISTERED");
        verify(memberships).findByParentAndFamily(42, 7);
    }

    @Test
    void malformedEndpointIsRejectedBeforePersistence() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(7));
        AuthContext auth = new AuthContext("family-1", 3, "child", "child@example.com", "csrf", "child");

        assertThatThrownBy(() -> service.register(auth,
            new WebPushSubscriptionRequest("http://not-push", "key", "auth")))
            .isInstanceOf(IllegalArgumentException.class);
        verify(subscriptions, never()).persist(any(WebPushSubscriptionEntity.class));
    }

    @Test
    void parentWithoutParentAccountIdIsResolvedFromEmailAndFamily() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(7));
        when(subscriptions.findByEndpoint("https://push.example/sub")).thenReturn(Optional.empty());
        // EXPLAIN: A web (non-Telegram) parent session may not carry parentAccountId
        // EXPLAIN: in the auth cookie. Registration must resolve it from email + family.
        AuthContext auth = new AuthContext("family-1", null, "admin", "parent@example.com", "csrf",
            "family_admin", null);
        ParentAccountEntity parent = mock(ParentAccountEntity.class);
        when(parent.getId()).thenReturn(99);
        when(parentAccounts.findByEmail("parent@example.com")).thenReturn(Optional.of(parent));
        FamilyParentMembershipEntity membership = membership(99);
        when(memberships.findByParentAndFamily(99, 7)).thenReturn(Optional.of(membership));

        service.register(auth, new WebPushSubscriptionRequest("https://push.example/sub", "public-key", "auth"));

        ArgumentCaptor<WebPushSubscriptionEntity> captor = ArgumentCaptor.forClass(WebPushSubscriptionEntity.class);
        verify(subscriptions).persist(captor.capture());
        WebPushSubscriptionEntity subscription = captor.getValue();
        assertThat(subscription.getFamilyId()).isEqualTo(7);
        assertThat(subscription.getParentAccountId()).isEqualTo(99);
        assertThat(subscription.getActorType()).isEqualTo("parent");
        verify(audits).write(7, 99, "parent@example.com", null, "web_push_subscription", "REGISTERED");
    }

    @Test
    void parentWithoutResolvableAccountIsRejected() {
        when(families.getDbId("family-1")).thenReturn(Optional.of(7));
        AuthContext auth = new AuthContext("family-1", null, "admin", "unknown@example.com", "csrf",
            "family_admin", null);
        when(parentAccounts.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(auth,
            new WebPushSubscriptionRequest("https://push.example/sub", "public-key", "auth")))
            .isInstanceOf(SecurityException.class);
        verify(subscriptions, never()).persist(any(WebPushSubscriptionEntity.class));
    }

    private static FamilyParentMembershipEntity membership(int parentAccountId) {
        FamilyParentMembershipEntity entity = mock(FamilyParentMembershipEntity.class);
        when(entity.getParentAccountId()).thenReturn(parentAccountId);
        return entity;
    }
}
