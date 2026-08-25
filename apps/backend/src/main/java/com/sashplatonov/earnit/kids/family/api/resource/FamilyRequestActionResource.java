package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.api.request.CreateRequestNoteRequest;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.family.application.action.FamilyActionService;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.platform.realtime.WebSocketNotificationService;
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
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Family request actions")
public class FamilyRequestActionResource extends FamilyCommandResourceSupport {
  @Inject
  public FamilyRequestActionResource(
      FamilyActionService action,
      FamilyService family,
      WebSocketNotificationService websocket,
      FamilyParentAccessService parentAccess) {
    super(action, family, websocket, parentAccess);
  }

  @POST
  @Path("/shop/{itemId}/request")
  @Operation(summary = "Create a child purchase request")
  public Response requestItemPurchase(
      @Context ContainerRequestContext ctx,
      @PathParam("itemId") long itemId,
      @QueryParam("childId") Integer childId,
      @Valid CreateRequestNoteRequest payload) {
    var auth = getAuthOrFail(ctx);
    if (auth == null || !auth.isChild() && !auth.canEditFamilyData()) {
      return unauthorized();
    }
    Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;
    if (effectiveChildId == null) {
      return badRequest(
          com.sashplatonov.earnit.kids.i18n.BackendMessages.message("errors.childIdRequired"));
    }
    OperationResult<FamilyDataResponse> result =
        familyActionService.requestItemPurchase(
            auth.familyId(), effectiveChildId, itemId, payload != null ? payload.note() : null);
    notifyDataUpdated(auth, effectiveChildId, result);
    return toResponse(result);
  }

  @POST
  @Path("/tasks/{taskId}/request")
  @Operation(summary = "Create a child task completion request")
  public Response requestTaskCompletion(
      @Context ContainerRequestContext ctx,
      @PathParam("taskId") long taskId,
      @QueryParam("childId") Integer childId,
      @Valid CreateRequestNoteRequest payload) {
    var auth = getAuthOrFail(ctx);
    if (auth == null || !auth.isChild() && !auth.canEditFamilyData()) {
      return unauthorized();
    }
    Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;
    if (effectiveChildId == null) {
      return badRequest(
          com.sashplatonov.earnit.kids.i18n.BackendMessages.message("errors.childIdRequired"));
    }
    OperationResult<FamilyDataResponse> result =
        familyActionService.requestTaskCompletion(
            auth.familyId(), effectiveChildId, taskId, payload != null ? payload.note() : null);
    notifyDataUpdated(auth, effectiveChildId, result);
    return toResponse(result);
  }

  @POST
  @Path("/requests/{requestId}/approve")
  @Operation(summary = "Approve a child request")
  public Response approveRequest(
      @Context ContainerRequestContext ctx,
      @PathParam("requestId") long requestId,
      @QueryParam("childId") Integer childId) {
    return mutateRequest(ctx, childId, requestId, true);
  }

  @POST
  @Path("/requests/{requestId}/reject")
  @Operation(summary = "Reject a child request")
  public Response rejectRequest(
      @Context ContainerRequestContext ctx,
      @PathParam("requestId") long requestId,
      @QueryParam("childId") Integer childId) {
    return mutateRequest(ctx, childId, requestId, false);
  }

  private Response mutateRequest(
      ContainerRequestContext ctx, Integer childId, long requestId, boolean approve) {
    var auth = getAuthOrFail(ctx);
    if (auth == null || !auth.canEditFamilyData()) {
      return unauthorized();
    }
    OperationResult<FamilyDataResponse> result =
        approve
            ? familyActionService.approveRequest(auth.familyId(), childId, requestId)
            : familyActionService.rejectRequest(auth.familyId(), childId, requestId);
    notifyDataUpdated(auth, childId, result);
    return toResponse(result);
  }

  @DELETE
  @Path("/requests/{requestId}")
  @Operation(summary = "Delete a request")
  public Response deleteRequest(
      @Context ContainerRequestContext ctx,
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
}
