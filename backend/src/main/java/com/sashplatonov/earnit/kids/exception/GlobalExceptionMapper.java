package com.sashplatonov.earnit.kids.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import org.jboss.logging.Logger;

/**
 * Global exception mapper that converts all unhandled exceptions to JSON error responses.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof ConstraintViolationException cve) {
            String message = cve.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(message, "VALIDATION_ERROR", 400))
                .build();
        }

        LOG.error("Unhandled exception", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.of("Internal server error", "INTERNAL_ERROR", 500))
            .build();
    }
}
