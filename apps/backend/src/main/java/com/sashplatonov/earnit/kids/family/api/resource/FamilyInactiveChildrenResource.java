package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.platform.realtime.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Inactive child profiles")
public class FamilyInactiveChildrenResource extends FamilyResourceSupport {
  @Inject
  public FamilyInactiveChildrenResource(
      FamilyService familyService,
      WebSocketNotificationService webSocketNotificationService,
      FamilyParentAccessService familyParentAccessService) {
    super(familyService, webSocketNotificationService, familyParentAccessService);
  }

  @GET
  @Path("/children/inactive")
  @Operation(summary = "List inactive child profiles for the family")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Inactive children listed",
        content =
            @Content(
                schema =
                    @Schema(
                        implementation =
                            com.sashplatonov.earnit.kids.family.api.response.ChildDto.class))),
    @APIResponse(
        responseCode = "400",
        description = "Listing failed",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @APIResponse(
        responseCode = "401",
        description = "Admin authentication required",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Response listInactiveChildren(@Context ContainerRequestContext ctx) {
    var auth = getAuthOrFail(ctx);
    if (auth == null || !auth.isAdmin()) {
      return unauthorized();
    }
    OperationResult<java.util.List<com.sashplatonov.earnit.kids.family.api.response.ChildDto>> result =
        familyService.listInactiveChildren(auth.familyId());
    return toResponse(result);
  }
}
