package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.api.request.UpdateNotificationPreferenceRequest;
import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.family.application.notification.FamilyNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.OperationResultResponses;
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

import java.util.function.Supplier;

@Path("/api/family/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FamilyNotificationResource extends ResourceAuthSupport {
    private final Supplier<FamilyNotificationService> notifications;

    @Inject
    public FamilyNotificationResource(FamilyNotificationService notifications) {
        this.notifications = () -> notifications;
    }

    @GET
    public Response settings(@Context ContainerRequestContext context) {
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        return response(notifications.get().getSettings(auth.familyId()));
    }

    @PUT
    public Response update(@Context ContainerRequestContext context,
                           @Valid UpdateNotificationPreferenceRequest request) {
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        OperationResult<Void> result = notifications.get().setPreference(
            auth.familyId(), request.scope(), request.childId(), request.key(), request.enabled());
        return OperationResultResponses.toVoidOk(result);
    }

    private <T> Response response(OperationResult<T> result) {
        return OperationResultResponses.toOk(result);
    }
}
