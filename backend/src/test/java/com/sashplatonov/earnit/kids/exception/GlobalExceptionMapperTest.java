package com.sashplatonov.earnit.kids.exception;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionMapperTest {

    private final GlobalExceptionMapper mapper = new GlobalExceptionMapper();

    @Test
    void toResponse_unhandledException_returnsInternalServerError() {
        Response response = mapper.toResponse(new RuntimeException("boom"));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(String.valueOf(response.getEntity())).contains("INTERNAL_ERROR");
    }
}
