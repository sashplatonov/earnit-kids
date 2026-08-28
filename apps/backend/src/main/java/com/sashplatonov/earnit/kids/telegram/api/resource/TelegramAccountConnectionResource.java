package com.sashplatonov.earnit.kids.telegram.api.resource;

import com.sashplatonov.earnit.kids.telegram.api.request.TelegramLinkCompletionRequest;
import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramAccountConnectionService;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.OperationResultResponses;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/telegram/account-connection")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TelegramAccountConnectionResource extends ResourceAuthSupport {
    private final TelegramAccountConnectionService connections;
    private final TelegramFeatureGate featureGate;

    @Inject
    public TelegramAccountConnectionResource(TelegramAccountConnectionService connections,
                                             TelegramFeatureGate featureGate) {
        this.connections = connections;
        this.featureGate = featureGate;
    }

    @GET
    public Response connection(@Context ContainerRequestContext context) {
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        return response(connections.connectionByParentId(auth.familyId(), auth.parentAccountId()));
    }

    @POST
    @Path("/start")
    public Response start(@Context ContainerRequestContext context) {
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        if (!featureGate.isMiniAppEnabled(auth.familyId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return response(connections.start(auth.familyId(), auth.email()));
    }

    @POST
    @Path("/complete")
    public Response complete(@Valid TelegramLinkCompletionRequest request) {
        if (!featureGate.isEnabled()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return response(connections.complete(request.token(), request.initData()));
    }

    @DELETE
    public Response unlink(@Context ContainerRequestContext context) {
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        return response(connections.unlink(auth.familyId(), auth.email()));
    }

    private <T> Response response(OperationResult<T> result) {
        return OperationResultResponses.toOk(result);
    }
}
