package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Path("/api/client-errors")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClientErrorResource {

    @POST
    public Response reportClientError(Map<String, Object> payload) {
        String type = safeString(payload.get("type"));
        String message = sanitize(safeString(payload.get("message")));
        String event = safeString(payload.get("event"));
        String source = safeString(payload.get("source"));
        String href = safeString(payload.get("href"));
        String path = safeString(payload.get("path"));
        String search = safeString(payload.get("search"));
        String traceId = safeString(payload.get("traceId"));
        String userAgent = safeString(payload.get("userAgent"));
        String status = safeString(payload.get("status"));
        String buildVersion = safeString(payload.get("buildVersion"));

        log.warn(
            "Client runtime error reported: event={}, type={}, status={}, message={}, source={}, href={}, path={}, search={}, traceId={}, userAgent={}, buildVersion={}",
            event,
            type,
            status,
            message,
            source,
            href,
            path,
            search,
            traceId,
            sanitize(userAgent),
            buildVersion
        );

        return Response.accepted(SimpleResponse.ok()).build();
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String sanitize(String value) {
        return value
            .replaceAll("(?i)(password|token|authorization)=\\S+", "$1=***")
            .replaceAll("(?i)bearer\\s+[A-Za-z0-9._-]+", "Bearer ***");
    }
}
