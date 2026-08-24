package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.api.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.family.application.action.FamilyActionService;
import com.sashplatonov.earnit.kids.platform.realtime.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
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
@Tag(name = "Family", description = "Family history actions")
public class FamilyHistoryResource extends FamilyCommandResourceSupport {
    @Inject
    public FamilyHistoryResource(FamilyActionService action, FamilyService family, WebSocketNotificationService websocket,
                                 FamilyParentAccessService parentAccess) {
        super(action, family, websocket, parentAccess);
    }
    @DELETE @Path("/history/{historyEntryId}")
    @Operation(summary = "Reverse a history entry without deleting its audit record")
    public Response deleteHistoryEntry(@Context ContainerRequestContext ctx, @PathParam("historyEntryId") long historyEntryId,
                                       @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) return unauthorized();
        if (childId == null) return badRequest(com.sashplatonov.earnit.kids.i18n.BackendMessages.message("errors.childIdRequired"));
        OperationResult<FamilyDataResponse> result = familyActionService.deleteHistoryEntry(auth.familyId(), childId, historyEntryId);
        notifyDataUpdated(auth, childId, result);
        return toResponse(result);
    }
}
