package com.sashplatonov.earnit.kids.platform.api;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiLogResourceTest {

    private final UiLogResource resource = new UiLogResource();

    @Test
    void receive_withoutMessage_returnsBadRequest() {
        assertThat(resource.receive(null).getStatus()).isEqualTo(400);
        assertThat(resource.receive(new UiLogMessage("error", null)).getStatus()).isEqualTo(400);
    }

    @Test
    void receive_supportedLevels_returnsOk() {
        for (String level : new String[] {"debug", "warn", "error", "info"}) {
            Response response = resource.receive(new UiLogMessage(level, "message"));
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void receive_missingOrUnknownLevel_usesInfoFallback() {
        assertThat(resource.receive(new UiLogMessage(null, "message")).getStatus()).isEqualTo(200);
        assertThat(resource.receive(new UiLogMessage("unknown", "message")).getStatus()).isEqualTo(200);
    }
}
