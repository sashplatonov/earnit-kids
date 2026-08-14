package com.sashplatonov.earnit.kids.resource.telegram;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.dto.request.TelegramLinkCompletionRequest;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.service.telegram.TelegramAccountConnectionService;
import com.sashplatonov.earnit.kids.service.telegram.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.util.OperationResult;
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
public class TelegramAccountConnectionResource {
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
        AuthContext auth = parentAuth(context);
        if (auth == null) {
            return unauthorized();
        }
        return response(connections.connection(auth.familyId(), auth.email()));
    }

    @POST
    @Path("/start")
    public Response start(@Context ContainerRequestContext context) {
        AuthContext auth = parentAuth(context);
        if (auth == null) {
            return unauthorized();
        }
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
        AuthContext auth = parentAuth(context);
        if (auth == null) {
            return unauthorized();
        }
        return response(connections.unlink(auth.familyId(), auth.email()));
    }

    private AuthContext parentAuth(ContainerRequestContext context) {
        Object value = context.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        if (!(value instanceof AuthContext auth) || !auth.isAdmin()) {
            return null;
        }
        return auth;
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
            .entity(ErrorResponse.unauthorized("Authentication is required."))
            .build();
    }

    private <T> Response response(OperationResult<T> result) {
        return switch (result) {
            case OperationResult.Success<T> success -> Response.ok(success.value()).build();
            case OperationResult.Failure<T> failure -> Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(failure.message(), failure.errorCode(), 400))
                .build();
        };
    }
}
