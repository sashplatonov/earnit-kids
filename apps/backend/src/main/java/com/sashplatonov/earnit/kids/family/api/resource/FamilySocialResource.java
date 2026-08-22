package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.api.request.AddFriendRequest;
import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import com.sashplatonov.earnit.kids.family.api.response.FriendDto;
import com.sashplatonov.earnit.kids.family.application.membership.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.platform.realtime.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
@Tag(name = "Family", description = "Family friends endpoints")
public class FamilySocialResource extends FamilyResourceSupport {

    @Inject
    public FamilySocialResource(FamilyService familyService,
                                WebSocketNotificationService webSocketNotificationService,
                                FamilyParentAccessService familyParentAccessService) {
        super(familyService, webSocketNotificationService, familyParentAccessService);
    }

    @GET
    @Path("/search-user")
    @Operation(summary = "Search other child profiles by nickname")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Matching profiles returned"),
        @APIResponse(responseCode = "401", description = "Child authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response searchUser(@Context ContainerRequestContext ctx,
                               @Parameter(description = "Nickname prefix to search for")
                               @QueryParam("nickname") String nickname) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isChild() || auth.childId() == null) {
            return unauthorized();
        }

        return toResponse(familyService.searchByNickname(nickname, auth.childId()));
    }

    @POST
    @Path("/add-friend")
    @Operation(summary = "Add another child as a friend")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Friend added",
            content = @Content(schema = @Schema(implementation = com.sashplatonov.earnit.kids.shared.api.response.SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Friend request failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Child authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response addFriend(@Context ContainerRequestContext ctx,
                              @RequestBody(required = true, description = "Friend relationship payload")
                              @Valid AddFriendRequest request) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isChild() || auth.childId() == null) {
            return unauthorized();
        }

        if (request.friendId() <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of("Friend id is required", "BAD_REQUEST", 400))
                .build();
        }

        return toVoidResponse(familyService.addFriend(auth.familyId(), auth.childId(), request.friendId()));
    }

    @GET
    @Path("/friends-list")
    @Operation(summary = "List friends for the authenticated child")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Friend list returned"),
        @APIResponse(responseCode = "401", description = "Child authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getFriendsList(@Context ContainerRequestContext ctx) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isChild() || auth.childId() == null) {
            return unauthorized();
        }

        return toResponse(familyService.getFriendsData(auth.childId()));
    }
}
