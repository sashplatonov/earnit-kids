package com.sashplatonov.earnit.kids.platform.api;

import com.sashplatonov.earnit.kids.resource.common.ClientErrorResource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClientErrorResourceTest {

    private final ClientErrorResource resource = new ClientErrorResource();

    @Test
    void reportClientError_acceptsPayload() {
        Response response = resource.reportClientError(Map.of(
            "type", "error",
            "message", "boom",
            "href", "http://localhost"
        ));

        assertThat(response.getStatus()).isEqualTo(202);
    }
}
