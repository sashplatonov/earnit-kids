package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.exception.ImportValidationException;
import com.sashplatonov.earnit.kids.family.api.request.ImportShopItemsRequest;
import com.sashplatonov.earnit.kids.family.api.request.ImportTasksRequest;
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
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Family imports")
public class FamilyImportResource extends FamilyCommandResourceSupport {
    @Inject
    public FamilyImportResource(FamilyActionService action, FamilyService family, WebSocketNotificationService websocket,
                                FamilyParentAccessService parentAccess) {
        super(action, family, websocket, parentAccess);
    }
    @POST @Path("/tasks/import")
    @Operation(summary = "Import tasks from CSV rows")
    public Response importTasks(@Context ContainerRequestContext ctx, @Valid ImportTasksRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) return unauthorized();
        try {
            FamilyDataResponse payload = familyActionService.importTasks(auth.familyId(), request);
            notifyDataUpdated(auth, request.childId(), OperationResult.success(payload));
            return Response.ok(payload).build();
        } catch (ImportValidationException exception) {
            return Response.status(Response.Status.BAD_REQUEST).entity(exception.response()).build();
        }
    }
    @POST @Path("/shop/import")
    @Operation(summary = "Import shop items from CSV rows")
    public Response importShopItems(@Context ContainerRequestContext ctx, @Valid ImportShopItemsRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) return unauthorized();
        try {
            FamilyDataResponse payload = familyActionService.importShopItems(auth.familyId(), request);
            notifyDataUpdated(auth, request.childId(), OperationResult.success(payload));
            return Response.ok(payload).build();
        } catch (ImportValidationException exception) {
            return Response.status(Response.Status.BAD_REQUEST).entity(exception.response()).build();
        }
    }
}
