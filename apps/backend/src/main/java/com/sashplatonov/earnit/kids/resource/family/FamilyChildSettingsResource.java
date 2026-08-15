package com.sashplatonov.earnit.kids.resource.family;

import com.sashplatonov.earnit.kids.dto.request.CreateChildRequest;
import com.sashplatonov.earnit.kids.dto.request.ChildTheme;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.request.UpdateChildSettingsRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateGroupOrderRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateNicknameRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateOwnNicknameRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateThemeRequest;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.service.family.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.service.websocket.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
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
@Tag(name = "Family", description = "Child settings endpoints")
public class FamilyChildSettingsResource extends FamilyResourceSupport {

    @Inject
    public FamilyChildSettingsResource(FamilyService familyService,
                                       WebSocketNotificationService webSocketNotificationService,
                                       FamilyParentAccessService familyParentAccessService) {
        super(familyService, webSocketNotificationService, familyParentAccessService);
    }

    @PUT
    @Path("/children/{childId}/nickname")
    @Operation(summary = "Rename a child profile")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Nickname updated",
            content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.dto.response.SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Rename failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateNickname(@Context ContainerRequestContext ctx,
                                   @Parameter(required = true, description = "Child id to rename")
                                   @PathParam("childId") int childId,
                                   @RequestBody(required = true, description = "New child nickname payload")
                                   @Valid UpdateNicknameRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.updateNickname(auth.familyId(), childId, request.name());
        notifyChildUpdated(auth.familyId(), childId, result);
        return toVoidResponse(result);
    }

    @POST
    @Path("/update-nickname")
    @Operation(summary = "Rename the authenticated child profile")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Nickname updated",
            content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.dto.response.SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Rename failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Child authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateOwnNickname(@Context ContainerRequestContext ctx,
                                      @RequestBody(required = true, description = "New nickname for the current child")
                                      @Valid UpdateOwnNicknameRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isChild() || auth.childId() == null) {
            return unauthorized();
        }

        OperationResult<Void> result =
            familyService.updateNickname(auth.familyId(), auth.childId(), request.nickname());
        notifyChildUpdated(auth.familyId(), auth.childId(), result);
        return toVoidResponse(result);
    }

    @PUT
    @Path("/children/{childId}/settings")
    @Operation(summary = "Update child limits and display name")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Child settings updated",
            content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.dto.response.SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Settings update failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateChildSettings(@Context ContainerRequestContext ctx,
                                        @Parameter(required = true, description = "Child id to update")
                                        @PathParam("childId") int childId,
                                        @RequestBody(required = true, description = "Updated child settings payload")
                                        @Valid UpdateChildSettingsRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.updateChildSettings(
            auth.familyId(), childId, request.name(), request.dailyCoinLimit(), request.monthlyLimit(),
            request.dailyRewardLimit());
        notifyChildUpdated(auth.familyId(), childId, result);
        return toVoidResponse(result);
    }

    @POST
    @Path("/children/{childId}/settings")
    @Operation(summary = "Update child limits and display name via POST alias")
    @APIResponse(responseCode = "200", description = "Child settings updated",
        content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.dto.response.SimpleResponse.class)))
    public Response updateChildSettingsPost(@Context ContainerRequestContext ctx,
                                            @Parameter(required = true, description = "Child id to update")
                                            @PathParam("childId") int childId,
                                            @RequestBody(required = true, description = "Updated child settings payload")
                                            @Valid UpdateChildSettingsRequest request) {
        return updateChildSettings(ctx, childId, request);
    }

    @PUT
    @Path("/children/{childId}/theme")
    @Operation(summary = "Update a child's theme")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Theme updated",
            content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.dto.response.SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Theme update failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateChildTheme(@Context ContainerRequestContext ctx,
                                     @Parameter(required = true, description = "Child id to update")
                                     @PathParam("childId") int childId,
                                     @RequestBody(required = true, description = "Theme selection payload")
                                     @Valid UpdateThemeRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || (!auth.isAdmin() && (!auth.isChild() || !java.util.Objects.equals(auth.childId(), childId)))) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.updateChildTheme(auth.familyId(), childId, request.theme());
        notifyChildUpdated(auth.familyId(), childId, result);
        return toVoidResponse(result);
    }

    @POST
    @Path("/children/{childId}/theme")
    @Operation(summary = "Update a child's theme via POST alias")
    @APIResponse(responseCode = "200", description = "Theme updated",
        content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.dto.response.SimpleResponse.class)))
    public Response updateChildThemePost(@Context ContainerRequestContext ctx,
                                         @Parameter(required = true, description = "Child id to update")
                                         @PathParam("childId") int childId,
                                         @RequestBody(required = true, description = "Theme selection payload")
                                         @Valid UpdateThemeRequest request) {
        return updateChildTheme(ctx, childId, request);
    }

    @POST
    @Path("/children/{childId}/group-order")
    @Operation(summary = "Update the saved group order for tasks or shop")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Group order updated",
            content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.dto.response.SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Group order update failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response updateChildGroupOrder(@Context ContainerRequestContext ctx,
                                          @Parameter(required = true, description = "Child id to update")
                                          @PathParam("childId") int childId,
                                          @RequestBody(required = true, description = "Group order payload")
                                          @Valid UpdateGroupOrderRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || (!auth.isAdmin() && (!auth.isChild() || !java.util.Objects.equals(auth.childId(), childId)))) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.updateChildGroupOrder(
            auth.familyId(), childId, request.section(), request.groups(), request.hiddenGroups(), auth.isChild());
        notifyChildUpdated(auth.familyId(), childId, result);
        return toVoidResponse(result);
    }

    @POST
    @Path("/children")
    @Operation(summary = "Create a new child profile")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Child created",
            content = @Content(schema = @Schema(implementation = ChildInfo.class))),
        @APIResponse(responseCode = "400", description = "Child creation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response createChild(@Context ContainerRequestContext ctx,
                                @RequestBody(required = true, description = "New child profile payload")
                                @Valid CreateChildRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        OperationResult<ChildInfo> result = familyService.createChild(auth.familyId(), request.name());
        notifyChildUpdated(auth.familyId(), result, childInfo -> childInfo.id());

        return switch (result) {
            case OperationResult.Success<ChildInfo> s ->
                Response.status(Response.Status.CREATED).entity(s.value()).build();
            case OperationResult.Failure<ChildInfo> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(f.message(), "CHILD_CREATE_FAILED", 400)).build();
        };
    }

    @DELETE
    @Path("/children/{childId}")
    @Operation(summary = "Delete a child profile")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Child deleted",
            content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.dto.response.SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Deletion failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response deleteChild(@Context ContainerRequestContext ctx,
                                @Parameter(required = true, description = "Child id to delete")
                                @PathParam("childId") int childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.deleteChild(auth.familyId(), childId);
        notifyChildDeleted(auth.familyId(), childId, result);
        return toVoidResponse(result);
    }

    @POST
    @Path("/children/{childId}/deactivate")
    @Operation(summary = "Deactivate a child profile without deleting data")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Child deactivated",
            content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.dto.response.SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Deactivation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response deactivateChild(@Context ContainerRequestContext ctx,
                                    @Parameter(required = true, description = "Child id to deactivate")
                                    @PathParam("childId") int childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.setChildActive(auth.familyId(), childId, false);
        notifyChildUpdated(auth.familyId(), childId, result);
        return toVoidResponse(result);
    }

    @POST
    @Path("/children/{childId}/reactivate")
    @Operation(summary = "Reactivate a previously deactivated child profile")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Child reactivated",
            content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.dto.response.SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Reactivation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response reactivateChild(@Context ContainerRequestContext ctx,
                                    @Parameter(required = true, description = "Child id to reactivate")
                                    @PathParam("childId") int childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.setChildActive(auth.familyId(), childId, true);
        notifyChildUpdated(auth.familyId(), childId, result);
        return toVoidResponse(result);
    }

    @GET
    @Path("/children/inactive")
    @Operation(summary = "List inactive child profiles for the family")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Inactive children listed",
            content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.dto.response.ChildDto.class))),
        @APIResponse(responseCode = "400", description = "Listing failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listInactiveChildren(@Context ContainerRequestContext ctx) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        OperationResult<List<com.sashplatonov.earnit.kids.dto.response.ChildDto>> result =
            familyService.listInactiveChildren(auth.familyId());
        return toResponse(result);
    }
}
