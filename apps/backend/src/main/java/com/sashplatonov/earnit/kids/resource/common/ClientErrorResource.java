package com.sashplatonov.earnit.kids.resource.common;

import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.shared.api.response.SimpleResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.container.ContainerRequestContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Set;

@Slf4j
@Path("/api/client-errors")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClientErrorResource {

    private static final int MAX_FIELD_LENGTH = 80;
    private static final Set<String> EVENT_CODES = Set.of(
        "web.server_error", "web.proxy_failure", "web.session_failure"
    );

    @POST
    public Response reportClientError(ClientErrorMessage payload, @Context ContainerRequestContext request) {
        if (payload == null || request.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY) == null
            || !EVENT_CODES.contains(payload.eventCode()) || !valid(payload)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        String traceId = firstNonBlank(sanitize(payload.traceId()), MDC.get("traceId"));

        log.warn(
            "diagnostic eventCode={} route={} status={} category={} traceId={} errorClass={}",
            payload.eventCode(),
            sanitize(payload.route()),
            payload.status(),
            sanitize(payload.category()),
            traceId,
            sanitize(payload.errorClass())
        );

        return Response.accepted(SimpleResponse.ok()).build();
    }

    private boolean valid(ClientErrorMessage payload) {
        return payload.route() != null && payload.category() != null && payload.errorClass() != null
            && payload.route().length() <= MAX_FIELD_LENGTH
            && payload.category().length() <= MAX_FIELD_LENGTH
            && payload.errorClass().length() <= MAX_FIELD_LENGTH;
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replaceAll("[\\r\\n\\t]", " ")
            .replaceAll("(?i)(password|token|authorization)=\\S+", "$1=***")
            .replaceAll("(?i)bearer\\s+[A-Za-z0-9._-]+", "Bearer ***")
            .substring(0, Math.min(value.length(), MAX_FIELD_LENGTH));
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }
}
