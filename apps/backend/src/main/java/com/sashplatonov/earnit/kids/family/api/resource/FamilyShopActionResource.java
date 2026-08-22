package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.api.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.family.application.action.FamilyActionService;
import com.sashplatonov.earnit.kids.platform.realtime.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Family shop actions")
public class FamilyShopActionResource extends FamilyCommandResourceSupport {
    @Inject
    public FamilyShopActionResource(FamilyActionService action, FamilyService family, WebSocketNotificationService websocket,
                                    FamilyParentAccessService parentAccess) {
        super(action, family, websocket, parentAccess);
    }
    @POST
    @Path("/shop/{itemId}/purchase")
    @Operation(summary = "Purchase a shop item immediately")
    public Response purchaseItem(@Context ContainerRequestContext ctx, @PathParam("itemId") long itemId,
                                 @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) return unauthorized();
        if (childId == null) return badRequest(com.sashplatonov.earnit.kids.i18n.BackendMessages.message("errors.childIdRequired"));
        OperationResult<FamilyDataResponse> result = familyActionService.purchaseItem(auth.familyId(), childId, itemId);
        notifyDataUpdated(auth, childId, result);
        return toResponse(result);
    }
}
