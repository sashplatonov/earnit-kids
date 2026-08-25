package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.family.api.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.platform.realtime.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import com.sashplatonov.earnit.kids.util.OperationResultResponses;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Parent admin transfer endpoints")
public class FamilyParentTransferResource extends FamilyResourceSupport {
  @Inject
  public FamilyParentTransferResource(
      FamilyService familyService,
      WebSocketNotificationService webSocketNotificationService,
      FamilyParentAccessService familyParentAccessService) {
    super(familyService, webSocketNotificationService, familyParentAccessService);
  }

  @POST
  @Path("/parents/{membershipId}/transfer-admin")
  @Operation(summary = "Create a pending admin-transfer request for another parent")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Transfer request created",
        content = @Content(schema = @Schema(implementation = ParentMembershipDto.class))),
    @APIResponse(
        responseCode = "400",
        description = "Transfer failed",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @APIResponse(
        responseCode = "401",
        description = "Family admin authentication required",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Response transferAdmin(
      @Context ContainerRequestContext ctx,
      @Parameter(required = true, description = "Membership id to promote to admin")
          @PathParam("membershipId")
          int membershipId) {
    var auth = getAuthOrFail(ctx);
    if (auth == null || !auth.canManageMemberships()) {
      return unauthorized();
    }
    return OperationResultResponses.toMappedOk(
        familyParentAccessService.transferAdmin(
            membershipId, auth.familyId(), auth.parentAccountId(), auth.email()),
        dto -> dto,
        failure ->
            "PARENT_TRANSFER_REQUEST_PENDING_EXISTS".equals(failure.errorCode())
                ? Response.Status.CONFLICT.getStatusCode()
                : Response.Status.BAD_REQUEST.getStatusCode());
  }

  @POST
  @Path("/parents/transfer-requests/{requestId}/accept")
  @Operation(summary = "Accept a pending admin transfer request as the target parent")
  public Response acceptTransferRequest(
      @Context ContainerRequestContext ctx,
      @Parameter(required = true, description = "Transfer request id") @PathParam("requestId")
          int requestId) {
    var auth = getAuthOrFail(ctx);
    if (auth == null || auth.parentAccountId() == null) {
      return unauthorized();
    }
    return transferResult(
        familyParentAccessService.acceptTransferRequest(
            requestId, auth.familyId(), auth.parentAccountId(), auth.email()));
  }

  @POST
  @Path("/parents/transfer-requests/{requestId}/decline")
  @Operation(summary = "Decline an admin transfer request having the target parent")
  public Response declineTransferRequest(
      @Context ContainerRequestContext ctx,
      @Parameter(required = true, description = "Transfer request id") @PathParam("requestId")
          int requestId) {
    var auth = getAuthOrFail(ctx);
    if (auth == null || auth.parentAccountId() == null) {
      return unauthorized();
    }
    return transferResult(
        familyParentAccessService.declineTransferRequest(
            requestId, auth.familyId(), auth.parentAccountId(), auth.email()));
  }

  @POST
  @Path("/parents/transfer-requests/{requestId}/cancel")
  @Operation(summary = "Cancel a pending admin transfer request by the actor parent")
  public Response cancelTransferRequest(
      @Context ContainerRequestContext ctx,
      @Parameter(required = true, description = "Transfer request id") @PathParam("requestId")
          int requestId) {
    var auth = getAuthOrFail(ctx);
    if (auth == null || auth.parentAccountId() == null) {
      return unauthorized();
    }
    return transferResult(
        familyParentAccessService.cancelTransferRequest(
            requestId, auth.familyId(), auth.parentAccountId(), auth.email()));
  }

  private Response transferResult(
      com.sashplatonov.earnit.kids.util.OperationResult<ParentMembershipDto> result) {
    return OperationResultResponses.toMappedOk(
        result,
        dto -> dto,
        failure ->
            "PARENT_MEMBERSHIP_FORBIDDEN".equals(failure.errorCode())
                ? Response.Status.FORBIDDEN.getStatusCode()
                : Response.Status.BAD_REQUEST.getStatusCode());
  }
}
