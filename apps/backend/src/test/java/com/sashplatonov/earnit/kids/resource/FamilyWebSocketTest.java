package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.JwtService;
import com.sashplatonov.earnit.kids.service.WebSocketNotificationService;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.HandshakeRequest;
import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyWebSocketTest {

    @Mock JwtService jwtService;
    @Mock WebSocketNotificationService webSocketNotificationService;
    @Mock WebSocketConnection connection;
    @Mock HandshakeRequest handshakeRequest;

    private FamilyWebSocket familyWebSocket;

    @BeforeEach
    void setUp() {
        familyWebSocket = new FamilyWebSocket(jwtService, webSocketNotificationService);
        lenient().when(connection.id()).thenReturn("conn-1");
    }

    @Test
    void onOpen_registersConnectionFromQueryToken() {
        when(handshakeRequest.header("Cookie")).thenReturn(null);
        when(handshakeRequest.query()).thenReturn("token=query.jwt");
        when(jwtService.verifyToken("query.jwt"))
            .thenReturn(Optional.of(Map.of("familyId", "fam-1", "role", "child", "childId", 10)));

        familyWebSocket.onOpen(connection, handshakeRequest);

        verify(webSocketNotificationService).register(eq("conn-1"), eq(new AuthContext("fam-1", 10, "child", null, null)));
    }

    @Test
    void onOpen_prefersCookieTokenWhenPresent() {
        when(handshakeRequest.header("Cookie")).thenReturn("app_auth=cookie.jwt; other=value");
        when(jwtService.verifyToken("cookie.jwt"))
            .thenReturn(Optional.of(Map.of("familyId", "fam-1", "role", "admin")));

        familyWebSocket.onOpen(connection, handshakeRequest);

        verify(webSocketNotificationService).register(eq("conn-1"), eq(new AuthContext("fam-1", null, "admin", null, null)));
    }

    @Test
    void onOpen_invalidTokens_closesConnection() {
        when(handshakeRequest.header("Cookie")).thenReturn(null);
        when(handshakeRequest.query()).thenReturn("token=bad.jwt");
        when(jwtService.verifyToken("bad.jwt")).thenReturn(Optional.empty());

        familyWebSocket.onOpen(connection, handshakeRequest);

        verify(connection).closeAndAwait(argThat(reason ->
            reason instanceof CloseReason closeReason
                && closeReason.getCode() == 4001
                && "Unauthorized".equals(closeReason.getMessage())));
    }

    @Test
    void onClose_unregistersConnection() {
        familyWebSocket.onClose(connection);

        verify(webSocketNotificationService).unregister("conn-1");
    }
}
