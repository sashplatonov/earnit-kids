package com.sashplatonov.earnit.kids.resource.family;

import com.sashplatonov.earnit.kids.dto.request.AdjustBalanceRequest;
import com.sashplatonov.earnit.kids.dto.request.BulkShopItemActionRequest;
import com.sashplatonov.earnit.kids.dto.request.BulkTaskActionRequest;
import com.sashplatonov.earnit.kids.dto.request.CreateRequestNoteRequest;
import com.sashplatonov.earnit.kids.dto.request.ImportShopItemsRequest;
import com.sashplatonov.earnit.kids.dto.request.ImportTasksRequest;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.ImportValidationErrorResponse;
import com.sashplatonov.earnit.kids.exception.ImportValidationException;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.service.family.action.FamilyActionService;
import com.sashplatonov.earnit.kids.service.family.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.service.websocket.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Family mutations and child-management endpoints")
public class FamilyResource extends FamilyResourceSupport {

    private final FamilyActionService familyActionService;

    @Inject
    public FamilyResource(FamilyActionService familyActionService,
                          FamilyService familyService,
                          WebSocketNotificationService webSocketNotificationService,
                          FamilyParentAccessService familyParentAccessService) {
        super(familyService, webSocketNotificationService, familyParentAccessService);
        this.familyActionService = familyActionService;
    }

    @POST
    @Path("/data")
    @Operation(summary = "Persist dashboard data mutations and return refreshed data")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Dashboard payload refreshed",
            content = @Content(schema = @Schema(implementation = FamilyDataResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response saveFamilyData(@Context ContainerRequestContext ctx,
                                   @RequestBody(required = true, description = "Client-side dashboard payload")
                                   java.util.Map<String, Object> payload) {
        var auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        var effectivePayload = payload == null ? java.util.Map.<String, Object>of() : payload;
        Integer requestedChildId = effectivePayload.get("childId") instanceof Number n ? n.intValue() : null;
        Integer childId = auth.isChild() ? auth.childId() : requestedChildId;
        OperationResult<FamilyDataResponse> result =
            familyService.saveFamilyData(auth.familyId(), childId, effectivePayload, auth.isAdmin());
        notifyDataUpdated(auth, childId, result);

        return toResponse(result);
    }

    @POST
    @Path("/tasks/{taskId}/complete")
    @Operation(summary = "Complete a task immediately and persist balance/history in one transaction")
    public Response completeTask(@Context ContainerRequestContext ctx,
                                 @PathParam("taskId") long taskId,
                                 @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }
        if (childId == null) {
            return badRequest(BackendMessages.message("errors.childIdRequired"));
        }

        OperationResult<FamilyDataResponse> result = familyActionService.completeTask(auth.familyId(), childId, taskId);
        notifyDataUpdated(auth, childId, result);
        return toResponse(result);
    }

    @POST
    @Path("/tasks/{taskId}/request")
    @Operation(summary = "Create a child task completion request immediately in the database")
    public Response requestTaskCompletion(@Context ContainerRequestContext ctx,
                                          @PathParam("taskId") long taskId,
                                          @Valid CreateRequestNoteRequest payload) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isChild()) {
            return unauthorized();
        }

        OperationResult<FamilyDataResponse> result = familyActionService.requestTaskCompletion(
            auth.familyId(),
            auth.childId(),
            taskId,
            payload != null ? payload.note() : null
        );
        notifyDataUpdated(auth, auth.childId(), result);
        return toResponse(result);
    }

    @POST
    @Path("/shop/{itemId}/purchase")
    @Operation(summary = "Purchase a shop item immediately and persist balance/history in one transaction")
    public Response purchaseItem(@Context ContainerRequestContext ctx,
                                 @PathParam("itemId") long itemId,
                                 @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }
        if (childId == null) {
            return badRequest(BackendMessages.message("errors.childIdRequired"));
        }

        OperationResult<FamilyDataResponse> result = familyActionService.purchaseItem(auth.familyId(), childId, itemId);
        notifyDataUpdated(auth, childId, result);
        return toResponse(result);
    }

    @POST
    @Path("/tasks/bulk")
    @Operation(summary = "Apply a bulk action to multiple tasks")
    public Response bulkTaskAction(@Context ContainerRequestContext ctx,
                                   @RequestBody(required = true, description = "Bulk task action payload")
                                   @Valid BulkTaskActionRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        OperationResult<FamilyDataResponse> result = familyActionService.bulkTaskAction(auth.familyId(), request);
        notifyDataUpdated(auth, request.childId(), result);
        return toResponse(result);
    }

    @POST
    @Path("/shop/bulk")
    @Operation(summary = "Apply a bulk action to multiple shop items")
    public Response bulkShopItemAction(@Context ContainerRequestContext ctx,
                                       @RequestBody(required = true, description = "Bulk shop item action payload")
                                       @Valid BulkShopItemActionRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        OperationResult<FamilyDataResponse> result = familyActionService.bulkShopItemAction(auth.familyId(), request);
        notifyDataUpdated(auth, request.childId(), result);
        return toResponse(result);
    }

    @POST
    @Path("/tasks/import")
    @Operation(summary = "Import tasks from CSV rows")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Tasks imported",
            content = @Content(schema = @Schema(implementation = FamilyDataResponse.class))),
        @APIResponse(responseCode = "400", description = "Import validation failed",
            content = @Content(schema = @Schema(implementation = ImportValidationErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response importTasks(@Context ContainerRequestContext ctx,
                                @RequestBody(required = true, description = "Task CSV import payload")
                                @Valid ImportTasksRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        try {
            FamilyDataResponse payload = familyActionService.importTasks(auth.familyId(), request);
            notifyDataUpdated(auth, request.childId(), OperationResult.success(payload));
            return Response.ok(payload).build();
        } catch (ImportValidationException exception) {
            return Response.status(Response.Status.BAD_REQUEST).entity(exception.response()).build();
        }
    }

    @POST
    @Path("/shop/import")
    @Operation(summary = "Import shop items from CSV rows")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Shop items imported",
            content = @Content(schema = @Schema(implementation = FamilyDataResponse.class))),
        @APIResponse(responseCode = "400", description = "Import validation failed",
            content = @Content(schema = @Schema(implementation = ImportValidationErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response importShopItems(@Context ContainerRequestContext ctx,
                                    @RequestBody(required = true, description = "Shop CSV import payload")
                                    @Valid ImportShopItemsRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        try {
            FamilyDataResponse payload = familyActionService.importShopItems(auth.familyId(), request);
            notifyDataUpdated(auth, request.childId(), OperationResult.success(payload));
            return Response.ok(payload).build();
        } catch (ImportValidationException exception) {
            return Response.status(Response.Status.BAD_REQUEST).entity(exception.response()).build();
        }
    }

    @POST
    @Path("/shop/{itemId}/request")
    @Operation(summary = "Create a child purchase request immediately in the database")
    public Response requestItemPurchase(@Context ContainerRequestContext ctx,
                                        @PathParam("itemId") long itemId,
                                        @Valid CreateRequestNoteRequest payload) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isChild()) {
            return unauthorized();
        }

        OperationResult<FamilyDataResponse> result = familyActionService.requestItemPurchase(
            auth.familyId(),
            auth.childId(),
            itemId,
            payload != null ? payload.note() : null
        );
        notifyDataUpdated(auth, auth.childId(), result);
        return toResponse(result);
    }

    @POST
    @Path("/requests/{requestId}/approve")
    @Operation(summary = "Approve a child request transactionally")
    public Response approveRequest(@Context ContainerRequestContext ctx,
                                   @PathParam("requestId") long requestId,
                                   @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        OperationResult<FamilyDataResponse> result =
            familyActionService.approveRequest(auth.familyId(), childId, requestId);
        notifyDataUpdated(auth, childId, result);
        return toResponse(result);
    }

    @POST
    @Path("/requests/{requestId}/reject")
    @Operation(summary = "Reject a child request transactionally")
    public Response rejectRequest(@Context ContainerRequestContext ctx,
                                  @PathParam("requestId") long requestId,
                                  @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        OperationResult<FamilyDataResponse> result =
            familyActionService.rejectRequest(auth.familyId(), childId, requestId);
        notifyDataUpdated(auth, childId, result);
        return toResponse(result);
    }

    @DELETE
    @Path("/requests/{requestId}")
    @Operation(summary = "Delete a request immediately from the database")
    public Response deleteRequest(@Context ContainerRequestContext ctx,
                                  @PathParam("requestId") long requestId,
                                  @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || (!auth.isAdmin() && !auth.isChild())) {
            return unauthorized();
        }

        Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;

        OperationResult<FamilyDataResponse> result =
            familyActionService.deleteRequest(auth.familyId(), effectiveChildId, requestId);
        notifyDataUpdated(auth, effectiveChildId, result);
        return toResponse(result);
    }

    @DELETE
    @Path("/history/{historyEntryId}")
    @Operation(summary = "Delete a history entry and reverse the child balance in one transaction")
    public Response deleteHistoryEntry(@Context ContainerRequestContext ctx,
                                       @PathParam("historyEntryId") long historyEntryId,
                                       @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }
        if (childId == null) {
            return badRequest(BackendMessages.message("errors.childIdRequired"));
        }

        OperationResult<FamilyDataResponse> result =
            familyActionService.deleteHistoryEntry(auth.familyId(), childId, historyEntryId);
        notifyDataUpdated(auth, childId, result);
        return toResponse(result);
    }

    @POST
    @Path("/balance/adjust")
    @Operation(summary = "Adjust child balance immediately and persist the audit history in one transaction")
    public Response adjustBalance(@Context ContainerRequestContext ctx,
                                  @RequestBody(required = true, description = "Balance adjustment payload")
                                  @Valid AdjustBalanceRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        OperationResult<FamilyDataResponse> result = familyActionService.adjustBalance(
            auth.familyId(),
            request.childId(),
            request.amount(),
            request.description()
        );
        notifyDataUpdated(auth, request.childId(), result);
        return toResponse(result);
    }
}
