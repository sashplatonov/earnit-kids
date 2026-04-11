package com.sashplatonov.earnit.kids.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionMapperTest {

    private final GlobalExceptionMapper mapper = new GlobalExceptionMapper();

    @Test
    void mapsConstraintViolationToBadRequest() {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path propertyPath = mock(Path.class);
        when(propertyPath.toString()).thenReturn("email");
        when(violation.getPropertyPath()).thenReturn(propertyPath);
        when(violation.getMessage()).thenReturn("must be valid");

        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));
        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(String.valueOf(response.getEntity())).contains("VALIDATION_ERROR");
    }

    @Test
    void mapsGenericExceptionToInternalServerError() {
        Response response = mapper.toResponse(new RuntimeException("boom"));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(String.valueOf(response.getEntity())).contains("INTERNAL_ERROR");
    }
}
