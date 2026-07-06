package com.sashplatonov.earnit.kids.exception;

import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        var message = exception.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .reduce((left, right) -> left + "; " + right)
            .orElse(BackendMessages.message("errors.validationFailed"));

        log.error("Validation failed: {}", message, exception);

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.of(message, "VALIDATION_ERROR", 400))
            .build();
    }
}
