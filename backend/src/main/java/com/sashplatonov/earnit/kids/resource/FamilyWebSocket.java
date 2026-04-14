package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.JwtService;
import com.sashplatonov.earnit.kids.service.WebSocketNotificationService;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.HandshakeRequest;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@WebSocket(path = "/ws")
@Slf4j
public class FamilyWebSocket {

    private final JwtService jwtService;
    private final WebSocketNotificationService webSocketNotificationService;

    @Inject
    public FamilyWebSocket(JwtService jwtService,
                           WebSocketNotificationService webSocketNotificationService) {
        this.jwtService = jwtService;
        this.webSocketNotificationService = webSocketNotificationService;
    }

    @OnOpen
    public void onOpen(WebSocketConnection connection, HandshakeRequest request) {
        Optional<AuthContext> authOpt = resolveAuth(request);
        if (authOpt.isEmpty()) {
            connection.closeAndAwait(new CloseReason(4001, "Unauthorized"));
            return;
        }

        AuthContext auth = authOpt.get();
        webSocketNotificationService.register(connection.id(), auth);
        log.info("WebSocket connected connectionId={} familyId={} role={} childId={}",
            connection.id(), auth.familyId(), auth.role(), auth.childId());
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        webSocketNotificationService.unregister(connection.id());
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable error) {
        webSocketNotificationService.unregister(connection.id());
        log.warn("WebSocket error connectionId={}", connection.id(), error);
    }

    @OnTextMessage
    public void onMessage(String message) {
    }

    Optional<AuthContext> resolveAuth(HandshakeRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        String cookieToken = readCookie(request.header("Cookie"), "app_auth");
        if (cookieToken != null) {
            Optional<AuthContext> auth = toAuthContext(cookieToken);
            if (auth.isPresent()) {
                return auth;
            }
        }

        String queryToken = readQueryToken(request.query());
        if (queryToken != null) {
            return toAuthContext(queryToken);
        }

        return Optional.empty();
    }

    private Optional<AuthContext> toAuthContext(String token) {
        return jwtService.verifyToken(token)
            .map(payload -> AuthContext.fromPayload(payload, null))
            .filter(auth -> auth.familyId() != null && auth.role() != null);
    }

    private String readQueryToken(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        for (String pair : query.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String readCookie(String cookieHeader, String name) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length == 2 && name.equals(keyValue[0].trim())) {
                return keyValue[1].trim();
            }
        }
        return null;
    }
}
