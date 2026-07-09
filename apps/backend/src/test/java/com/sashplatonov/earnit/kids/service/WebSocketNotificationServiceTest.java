package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketNotificationServiceTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-04-16T12:00:00Z");

    @Mock OpenConnections openConnections;
    @Mock WebSocketConnection adminConnection;
    @Mock WebSocketConnection childConnection;

    private SimpleMeterRegistry meterRegistry;
    private BackendKpiMetrics backendKpiMetrics;
    private WebSocketNotificationService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        backendKpiMetrics = new BackendKpiMetrics(meterRegistry);
        service = new WebSocketNotificationService(
            openConnections,
            new ObjectMapper(),
            TestConfigFactory.timeProvider(FIXED_NOW),
            backendKpiMetrics);

        when(adminConnection.isOpen()).thenReturn(true);
        when(childConnection.isOpen()).thenReturn(true);
        lenient().when(openConnections.findByConnectionId("admin-1")).thenReturn(Optional.of(adminConnection));
        lenient().when(openConnections.findByConnectionId("child-1")).thenReturn(Optional.of(childConnection));

        service.register("admin-1", new AuthContext("fam-1", null, "admin", null, null, false, "family_admin"));
        service.register("child-1", new AuthContext("fam-1", 10, "child", null, null, false, "child"));
    }

    @Test
    void notifyFamily_sendsToAllConnectionsInFamily() {
        service.notifyFamily("fam-1", "DATA_UPDATED", Map.of("by", "admin"));

        ArgumentCaptor<String> adminPayload = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> childPayload = ArgumentCaptor.forClass(String.class);
        verify(adminConnection).sendTextAndAwait(adminPayload.capture());
        verify(childConnection).sendTextAndAwait(childPayload.capture());
        assertThat(adminPayload.getValue()).contains("\"type\":\"DATA_UPDATED\"");
        assertThat(adminPayload.getValue()).contains("\"by\":\"admin\"");
        assertThat(adminPayload.getValue()).contains("2026-04-16T12:00:00Z");
        assertThat(childPayload.getValue()).contains("\"type\":\"DATA_UPDATED\"");
        assertThat(meterRegistry.find("earnit.backend.websocket.active.sessions").gauge()).isNotNull();
        assertThat(meterRegistry.find("earnit.backend.websocket.notification.count")
            .tags("service", "websocket", "operation", "family", "outcome", "sent")
            .counter()).isNotNull();
    }

    @Test
    void notifyAdmins_filtersChildConnectionsOut() {
        service.notifyAdmins("fam-1", "CHILD_UPDATED", Map.of("childId", 10));

        verify(adminConnection).sendTextAndAwait(anyString());
        verify(childConnection, never()).sendTextAndAwait(anyString());
    }

    @Test
    void broadcast_skipsMissingConnections() {
        when(openConnections.findByConnectionId("child-1")).thenReturn(Optional.empty());

        service.broadcast("PING", Map.of("ok", true));

        verify(adminConnection).sendTextAndAwait(anyString());
    }
}
