package com.sashplatonov.earnit.kids.resource.telegram;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.TelegramLinkLaunchResponse;
import com.sashplatonov.earnit.kids.service.telegram.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.service.telegram.TelegramParentInvitationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/telegram/parents/invite")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TelegramParentInviteResource {
    private final TelegramParentInvitationService invitations;
    private final TelegramFeatureGate featureGate;
    private final TimeProvider timeProvider;

    @Inject
    public TelegramParentInviteResource(TelegramParentInvitationService invitations,
                                        TelegramFeatureGate featureGate,
                                        TimeProvider timeProvider) {
        this.invitations = invitations;
        this.featureGate = featureGate;
        this.timeProvider = timeProvider;
    }

    @POST
    public Response invite(@Context ContainerRequestContext context) {
        AuthContext auth = parentAuth(context);
        if (auth == null) {
            return unauthorized();
        }
        if (!featureGate.isMiniAppEnabled(auth.familyId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return response(invitations.invite(auth.familyId(), auth.email(), timeProvider.now()));
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