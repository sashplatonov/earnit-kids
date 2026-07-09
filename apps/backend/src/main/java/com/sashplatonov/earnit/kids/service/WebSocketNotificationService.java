package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.dto.response.WebSocketEventResponse;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import io.quarkus.websockets.next.OpenConnections;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

@ApplicationScoped
@Slf4j
public class WebSocketNotificationService {
    private static final String NOTIFICATION_COUNT_METRIC = "earnit.backend.websocket.notification.count";
    private static final String ACTIVE_SESSIONS_METRIC = "earnit.backend.websocket.active.sessions";

    private final OpenConnections openConnections;
    private final ObjectMapper objectMapper;
    private final TimeProvider timeProvider;
    private final BackendKpiMetrics backendKpiMetrics;
    private final ConcurrentMap<String, WebSocketSessionInfo> sessions = new ConcurrentHashMap<>();

    @Inject
    public WebSocketNotificationService(OpenConnections openConnections, ObjectMapper objectMapper,
                                        TimeProvider timeProvider, BackendKpiMetrics backendKpiMetrics) {
        this.openConnections = openConnections;
        this.objectMapper = objectMapper;
        this.timeProvider = timeProvider;
        this.backendKpiMetrics = backendKpiMetrics;
        backendKpiMetrics.registerGauge(
            ACTIVE_SESSIONS_METRIC,
            sessions,
            "websocket",
            "Active websocket sessions");
    }

    public void register(String connectionId, AuthContext auth) {
        if (connectionId == null
            || connectionId.isBlank()
            || auth == null
            || auth.familyId() == null
            || auth.role() == null) {
            return;
        }

        sessions.put(connectionId, new WebSocketSessionInfo(auth.familyId(), auth.childId(), auth.role()));
    }

    public void unregister(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return;
        }
        sessions.remove(connectionId);
    }

    public void notifyFamily(String familyId, String type, Object data) {
        send("family", type, data, session -> Objects.equals(session.familyId(), familyId));
    }

    public void notifyAdmins(String familyId, String type, Object data) {
        send("admins", type, data, session -> Objects.equals(session.familyId(), familyId) && session.isAdmin());
    }

    public void broadcast(String type, Object data) {
        send("broadcast", type, data, session -> true);
    }

    private void send(String scope, String type, Object data, Predicate<WebSocketSessionInfo> filter) {
        if (type == null || type.isBlank()) {
            return;
        }

        String message = encodeMessage(type, data);
        if (message == null) {
            return;
        }

        for (Map.Entry<String, WebSocketSessionInfo> entry : sessions.entrySet()) {
            if (!filter.test(entry.getValue())) {
                continue;
            }

            String connectionId = entry.getKey();
            var connectionOpt = openConnections.findByConnectionId(connectionId);
            if (connectionOpt.isEmpty() || !connectionOpt.get().isOpen()) {
                sessions.remove(connectionId);
                backendKpiMetrics.increment(NOTIFICATION_COUNT_METRIC, "websocket", scope, "stale");
                continue;
            }

            try {
                connectionOpt.get().sendTextAndAwait(message);
                backendKpiMetrics.increment(NOTIFICATION_COUNT_METRIC, "websocket", scope, "sent");
            } catch (RuntimeException ex) {
                log.warn("Failed to send websocket event type={} connectionId={}", type, connectionId, ex);
                backendKpiMetrics.increment(NOTIFICATION_COUNT_METRIC, "websocket", scope, "failure");
                if (connectionOpt.get().isClosed()) {
                    sessions.remove(connectionId);
                }
            }
        }
    }

    private String encodeMessage(String type, Object data) {
        try {
            return objectMapper.writeValueAsString(
                new WebSocketEventResponse(type, data, timeProvider.now().toString())
            );
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize websocket event type={}", type, ex);
            backendKpiMetrics.increment(NOTIFICATION_COUNT_METRIC, "websocket", "serialize", "failure");
            return null;
        }
    }
}
