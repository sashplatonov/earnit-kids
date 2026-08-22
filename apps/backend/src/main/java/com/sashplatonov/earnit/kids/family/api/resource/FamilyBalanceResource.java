package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.api.request.AdjustBalanceRequest;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.family.application.action.FamilyActionService;
import com.sashplatonov.earnit.kids.platform.realtime.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Family balance actions")
public class FamilyBalanceResource extends FamilyCommandResourceSupport {
    @Inject
    public FamilyBalanceResource(FamilyActionService action, FamilyService family, WebSocketNotificationService websocket,
                                 FamilyParentAccessService parentAccess) {
        super(action, family, websocket, parentAccess);
    }
    @POST @Path("/balance/adjust")
    @Operation(summary = "Adjust child balance and persist the audit history")
    public Response adjustBalance(@Context ContainerRequestContext ctx, @RequestBody @Valid AdjustBalanceRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) return unauthorized();
        OperationResult<FamilyDataResponse> result = familyActionService.adjustBalance(
            auth.familyId(), request.childId(), request.amount(), request.description());
        notifyDataUpdated(auth, request.childId(), result);
        return toResponse(result);
    }
}
