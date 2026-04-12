package com.sashplatonov.earnit.kids.exception;

import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        log.error("Unhandled exception while processing REST request", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.of("Internal server error", "INTERNAL_ERROR", 500))
            .build();
    }
}
