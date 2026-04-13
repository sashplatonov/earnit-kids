package com.sashplatonov.earnit.kids.exception;

import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        var message = exception.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .reduce((left, right) -> left + "; " + right)
            .orElse("Validation failed");

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.of(message, "VALIDATION_ERROR", 400))
            .build();
    }
}
