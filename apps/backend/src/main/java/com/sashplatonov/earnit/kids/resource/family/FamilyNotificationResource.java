package com.sashplatonov.earnit.kids.resource.family;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.dto.request.UpdateNotificationPreferenceRequest;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyNotificationSettingsResponse;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import com.sashplatonov.earnit.kids.service.family.FamilyNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/family/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FamilyNotificationResource {
    private final FamilyNotificationService notifications;

    @Inject
    public FamilyNotificationResource(FamilyNotificationService notifications) {
        this.notifications = notifications;
    }

    @GET
    public Response settings(@Context ContainerRequestContext context) {
        AuthContext auth = parentAuth(context);
        if (auth == null) {
            return unauthorized();
        }
        return response(notifications.getSettings(auth.familyId()));
    }

    @PUT
    public Response update(@Context ContainerRequestContext context,
                           @Valid UpdateNotificationPreferenceRequest request) {
        AuthContext auth = parentAuth(context);
        if (auth == null) {
            return unauthorized();
        }
        OperationResult<Void> result = notifications.setPreference(
            auth.familyId(), request.scope(), request.childId(), request.key(), request.enabled());
        return switch (result) {
            case OperationResult.Success<Void> ignored -> Response.ok(SimpleResponse.ok()).build();
            case OperationResult.Failure<Void> failure -> Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(failure.message(), failure.errorCode(), 400))
                .build();
        };
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
