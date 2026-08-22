package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.api.request.BulkTaskActionRequest;
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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Family task actions")
public class FamilyTaskActionResource extends FamilyCommandResourceSupport {

    @Inject
    public FamilyTaskActionResource(FamilyActionService familyActionService, FamilyService familyService,
                                    WebSocketNotificationService webSocketNotificationService,
                                    FamilyParentAccessService familyParentAccessService) {
        super(familyActionService, familyService, webSocketNotificationService, familyParentAccessService);
    }

    @POST
    @Path("/data")
    @Operation(summary = "Persist dashboard data mutations and return refreshed data")
    public Response saveFamilyData(@Context ContainerRequestContext ctx, @RequestBody Map<String, Object> payload) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) return unauthorized();
        var effectivePayload = payload == null ? Map.<String, Object>of() : payload;
        Integer childId = effectivePayload.get("childId") instanceof Number n ? n.intValue() : null;
        OperationResult<FamilyDataResponse> result = familyService.saveFamilyData(
            auth.familyId(), childId, effectivePayload, auth.isAdmin());
        notifyDataUpdated(auth, childId, result);
        return toResponse(result);
    }

    @POST
    @Path("/tasks/{taskId}/complete")
    @Operation(summary = "Complete a task immediately")
    public Response completeTask(@Context ContainerRequestContext ctx, @PathParam("taskId") long taskId,
                                 @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) return unauthorized();
        if (childId == null) return badRequest(com.sashplatonov.earnit.kids.i18n.BackendMessages.message("errors.childIdRequired"));
        OperationResult<FamilyDataResponse> result = familyActionService.completeTask(auth.familyId(), childId, taskId);
        notifyDataUpdated(auth, childId, result);
        return toResponse(result);
    }

    @POST
    @Path("/tasks/bulk")
    @Operation(summary = "Apply a bulk action to multiple tasks")
    public Response bulkTaskAction(@Context ContainerRequestContext ctx, @RequestBody @Valid BulkTaskActionRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) return unauthorized();
        OperationResult<FamilyDataResponse> result = familyActionService.bulkTaskAction(auth.familyId(), request);
        notifyDataUpdated(auth, request.childId(), result);
        return toResponse(result);
    }

}
