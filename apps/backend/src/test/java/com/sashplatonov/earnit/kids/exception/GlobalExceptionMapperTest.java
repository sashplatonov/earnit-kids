package com.sashplatonov.earnit.kids.exception;

import jakarta.ws.rs.NotFoundException;
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

    @Test
    void toResponse_notFoundException_preservesNotFoundStatus() {
        Response response = mapper.toResponse(new NotFoundException("missing"));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(String.valueOf(response.getEntity())).contains("NOT_FOUND");
    }

    @Test
    void shouldLogAsError_onlyForServerErrors() {
        assertThat(mapper.shouldLogAsError(404)).isFalse();
        assertThat(mapper.shouldLogAsError(422)).isFalse();
        assertThat(mapper.shouldLogAsError(500)).isTrue();
    }

    @Test
    void shouldLogAsInfo_onlyForNotFound() {
        assertThat(mapper.shouldLogAsInfo(404)).isTrue();
        assertThat(mapper.shouldLogAsInfo(405)).isFalse();
        assertThat(mapper.shouldLogAsInfo(500)).isFalse();
    }
}
