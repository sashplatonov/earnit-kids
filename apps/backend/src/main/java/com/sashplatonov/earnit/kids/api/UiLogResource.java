package com.sashplatonov.earnit.kids.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

// EXPLAIN: REST endpoint used by the frontend to forward UI console messages to
// EXPLAIN: the backend log, so UI-side logs appear in the container's stdout.
@ApplicationScoped
@Path("/api/ui-log")
public class UiLogResource {

    private static final Logger LOG = Logger.getLogger(UiLogResource.class);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response receive(UiLogMessage msg) {
        if (msg == null || msg.message == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        String level = (msg.level != null) ? msg.level.toLowerCase() : "info";
        switch (level) {
            case "debug":
                LOG.debug(msg.message);
                break;
            case "warn":
                LOG.warn(msg.message);
                break;
            case "error":
                LOG.error(msg.message);
                break;
            case "info":
            default:
                LOG.info(msg.message);
                break;
        }
        return Response.ok().build();
    }
}
