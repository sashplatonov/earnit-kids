package com.sashplatonov.earnit.kids.telegram.api.resource;

import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramChildConnectionService;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.OperationResultResponses;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/children/{childId}/telegram")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TelegramChildConnectionResource extends ResourceAuthSupport {
    private final TelegramChildConnectionService connections;
    private final TelegramFeatureGate featureGate;

    @Inject
    public TelegramChildConnectionResource(TelegramChildConnectionService connections,
                                           TelegramFeatureGate featureGate) {
        this.connections = connections;
        this.featureGate = featureGate;
    }

    @GET
    public Response connection(@Context ContainerRequestContext context,
                               @PathParam("childId") int childId) {
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        return response(connections.connection(auth.familyId(), childId));
    }

    @POST
    @Path("/invite")
    public Response invite(@Context ContainerRequestContext context,
                           @PathParam("childId") int childId) {
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        if (!featureGate.isMiniAppEnabled(auth.familyId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return response(connections.invite(auth.familyId(), childId));
    }

    @POST
    @Path("/unlink")
    public Response unlink(@Context ContainerRequestContext context,
                           @PathParam("childId") int childId) {
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        return response(connections.unlink(auth.familyId(), childId));
    }

    private <T> Response response(OperationResult<T> result) {
        return OperationResultResponses.toOk(result);
    }
}
