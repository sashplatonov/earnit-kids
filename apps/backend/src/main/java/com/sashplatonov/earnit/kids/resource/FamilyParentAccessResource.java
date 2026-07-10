package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.dto.request.AddParentMembershipRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateParentMembershipRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdatePreferenceRequest;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.dto.response.TokenResponse;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.request.FamilyPreferenceKey;
import com.sashplatonov.earnit.kids.service.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.service.FamilyService;
import com.sashplatonov.earnit.kids.service.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
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
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Family membership and access endpoints")
public class FamilyParentAccessResource extends FamilyResourceSupport {

    @Inject
    public FamilyParentAccessResource(FamilyService familyService,
                                      WebSocketNotificationService webSocketNotificationService,
                                      FamilyParentAccessService familyParentAccessService) {
        super(familyService, webSocketNotificationService, familyParentAccessService);
    }

    @GET
    @Path("/children/{childId}/link")
    @Operation(summary = "Return the current login token for a child")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Token returned",
            content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Child not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getChildLink(@Context ContainerRequestContext ctx,
                                 @Parameter(required = true, description = "Child id to inspect")
                                 @PathParam("childId") int childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        OperationResult<String> result = familyService.getChildLoginLink(auth.familyId(), childId);
        return switch (result) {
            case OperationResult.Success<String> s -> Response.ok(new TokenResponse(s.value())).build();
            case OperationResult.Failure<String> f ->
                Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.of(f.message(), "CHILD_NOT_FOUND", 404)).build();
        };
    }

    @POST
    @Path("/children/{childId}/regenerate-token")
    @Operation(summary = "Regenerate the login token for a child")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "New token returned",
            content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Token could not be regenerated",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response regenerateChildToken(@Context ContainerRequestContext ctx,
                                         @Parameter(required = true, description = "Child id to update")
                                         @PathParam("childId") int childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        OperationResult<String> result = familyService.regenerateChildToken(auth.familyId(), childId);
        return switch (result) {
            case OperationResult.Success<String> s -> Response.ok(new TokenResponse(s.value())).build();
            case OperationResult.Failure<String> f ->
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.of(f.message(), "TOKEN_REGENERATION_FAILED", 500)).build();
        };
    }

    @POST
    @Path("/preferences")
    @Operation(summary = "Update a persisted family preference")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Preference updated",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Preference update failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updatePreference(@Context ContainerRequestContext ctx,
                                     @RequestBody(required = true, description = "Preference update payload")
                                     @Valid UpdatePreferenceRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        if (request.key() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of("Key is required", "BAD_REQUEST", 400))
                .build();
        }

        return toVoidResponse(familyService.updatePreference(auth.familyId(), request.key(), request.value()));
    }

    @GET
    @Path("/parents")
    @Operation(summary = "List parent memberships for the active family")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Membership list returned",
            content = @Content(schema = @Schema(implementation = ParentMembershipDto.class))),
        @APIResponse(responseCode = "401", description = "Family admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listParents(@Context ContainerRequestContext ctx) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canManageMemberships()) {
            return unauthorized();
        }

        return toResponse(familyParentAccessService.listMemberships(auth.familyId()));
    }

    @POST
    @Path("/parents")
    @Operation(summary = "Add a parent membership to the active family")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Membership created",
            content = @Content(schema = @Schema(implementation = ParentMembershipDto.class))),
        @APIResponse(responseCode = "400", description = "Membership creation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Family admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response addParent(@Context ContainerRequestContext ctx,
                              @RequestBody(required = true, description = "Add parent membership payload")
                              @NotNull @Valid AddParentMembershipRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canManageMemberships()) {
            return unauthorized();
        }

        var result = familyParentAccessService.addMembership(
            auth.familyId(), request.email(), request.permission(), auth.email());

        return switch (result) {
            case OperationResult.Success<ParentMembershipDto> s ->
                Response.status(Response.Status.CREATED).entity(s.value()).build();
            case OperationResult.Failure<ParentMembershipDto> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(f.message(), errorCodeOrBadRequest(f.errorCode()), 400)).build();
        };
    }

    @PUT
    @Path("/parents/{membershipId}")
    @Operation(summary = "Update a parent membership permission")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Membership updated",
            content = @Content(schema = @Schema(implementation = ParentMembershipDto.class))),
        @APIResponse(responseCode = "400", description = "Update failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Family admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateParent(@Context ContainerRequestContext ctx,
                                 @Parameter(required = true, description = "Membership id to update")
                                 @PathParam("membershipId") int membershipId,
                                 @RequestBody(required = true, description = "Update permission payload")
                                 @NotNull @Valid UpdateParentMembershipRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canManageMemberships()) {
            return unauthorized();
        }

        return toResponse(familyParentAccessService.updateMembership(
            membershipId, request.permission(), auth.familyId()));
    }

    @DELETE
    @Path("/parents/{membershipId}")
    @Operation(summary = "Remove a parent membership from the active family")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Membership removed",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Removal failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Family admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response removeParent(@Context ContainerRequestContext ctx,
                                 @Parameter(required = true, description = "Membership id to remove")
                                 @PathParam("membershipId") int membershipId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canManageMemberships()) {
            return unauthorized();
        }

        return toVoidResponse(familyParentAccessService.removeMembership(membershipId, auth.familyId(), auth.email()));
    }
}
