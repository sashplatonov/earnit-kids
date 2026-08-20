package com.sashplatonov.earnit.kids.resource.telegram;

import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.service.telegram.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.service.telegram.TelegramParentInvitationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.OperationResultResponses;
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
public class TelegramParentInviteResource extends ResourceAuthSupport {
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
        Response authFailure = requireAdminOrUnauthorized(context);
        if (authFailure != null) {
            return authFailure;
        }
        var auth = authContext(context);
        if (!featureGate.isMiniAppEnabled(auth.familyId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return response(invitations.invite(auth.familyId(), auth.email(), timeProvider.now()));
    }

    private <T> Response response(OperationResult<T> result) {
        return OperationResultResponses.toOk(result);
    }
}
