package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.dto.request.AddFriendRequest;
import com.sashplatonov.earnit.kids.dto.request.AdjustBalanceRequest;
import com.sashplatonov.earnit.kids.dto.request.CreateChildRequest;
import com.sashplatonov.earnit.kids.dto.request.CreateRequestNoteRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateGroupOrderRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateChildSettingsRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateNicknameRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateOwnNicknameRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdatePreferenceRequest;
import com.sashplatonov.earnit.kids.dto.request.UpdateThemeRequest;
import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import com.sashplatonov.earnit.kids.dto.response.TokenResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.service.BaseDataService;
import com.sashplatonov.earnit.kids.service.FamilyActionService;
import com.sashplatonov.earnit.kids.service.FamilyService;
import com.sashplatonov.earnit.kids.service.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.Objects;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Family dashboard, children, history, and analytics endpoints")
public class FamilyResource {

    private final FamilyActionService familyActionService;
    private final FamilyService familyService;
    private final BaseDataService baseDataService;
    private final WebSocketNotificationService webSocketNotificationService;

    @Inject
    public FamilyResource(FamilyActionService familyActionService,
                          FamilyService familyService,
                          BaseDataService baseDataService,
                          WebSocketNotificationService webSocketNotificationService) {
        this.familyActionService = familyActionService;
        this.familyService = familyService;
        this.baseDataService = baseDataService;
        this.webSocketNotificationService = webSocketNotificationService;
    }

    @GET
    @Path("/data")
    @Operation(summary = "Load the dashboard payload for a family or child session")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Dashboard payload returned",
            content = @Content(schema = @Schema(implementation = FamilyDataResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getFamilyData(@Context ContainerRequestContext ctx,
                                  @Parameter(description = "Child id override for admin sessions")
                                  @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;
        OperationResult<FamilyDataResponse> result =
            familyService.loadFamilyData(auth.familyId(), effectiveChildId, auth.isAdmin());

        return toResponse(result);
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
                                   Map<String, Object> payload) {
        var auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        var effectivePayload = payload == null ? Map.<String, Object>of() : payload;
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
        if (auth == null || !auth.isAdmin()) {
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
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        OperationResult<FamilyDataResponse> result = familyActionService.approveRequest(auth.familyId(), childId, requestId);
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
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        OperationResult<FamilyDataResponse> result = familyActionService.rejectRequest(auth.familyId(), childId, requestId);
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

        // EXPLAIN: Child sessions may only delete their own requests; ignore client-provided childId.
        Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;

        OperationResult<FamilyDataResponse> result = familyActionService.deleteRequest(auth.familyId(), effectiveChildId, requestId);
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
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }
        if (childId == null) {
            return badRequest(BackendMessages.message("errors.childIdRequired"));
        }

        OperationResult<FamilyDataResponse> result = familyActionService.deleteHistoryEntry(auth.familyId(), childId, historyEntryId);
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
        if (auth == null || !auth.isAdmin()) {
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

    @GET
    @Path("/base-data")
    @Operation(summary = "Load the static task and reward catalog")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Base catalog returned"),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getBaseData(@Context ContainerRequestContext ctx) {
        var auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        return Response.ok(baseDataService.getBaseData()).build();
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
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
        @APIResponse(responseCode = "400", description = "Deletion failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "401", description = "Admin authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response deleteChild(@Context ContainerRequestContext ctx,
                                @Parameter(required = true, description = "Child id to delete")
                                @PathParam("childId") int childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.deleteChild(auth.familyId(), childId);
        notifyChildDeleted(auth.familyId(), childId, result);
        return toVoidResponse(result);
    }

    @PUT
    @Path("/children/{childId}/nickname")
    @Operation(summary = "Rename a child profile")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Nickname updated",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
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
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.updateNickname(auth.familyId(), childId, request.name());
        notifyChildUpdated(auth.familyId(), childId, result);
        return toVoidResponse(result);
    }

    @PUT
    @Path("/children/{childId}/settings")
    @Operation(summary = "Update child limits and display name")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Child settings updated",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
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
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.updateChildSettings(
            auth.familyId(), childId, request.name(), request.dailyCoinLimit(), request.monthlyLimit());
        notifyChildUpdated(auth.familyId(), childId, result);
        return toVoidResponse(result);
    }

    @POST
    @Path("/children/{childId}/settings")
    @Operation(summary = "Update child limits and display name via POST alias")
    @APIResponse(responseCode = "200", description = "Child settings updated",
        content = @Content(schema = @Schema(implementation = SimpleResponse.class)))
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
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
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
        if (auth == null || (!auth.isAdmin() && (!auth.isChild() || !Objects.equals(auth.childId(), childId)))) {
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
        content = @Content(schema = @Schema(implementation = SimpleResponse.class)))
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
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
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
        if (auth == null || (!auth.isAdmin() && (!auth.isChild() || !Objects.equals(auth.childId(), childId)))) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.updateChildGroupOrder(
            auth.familyId(), childId, request.section(), request.groups(), auth.isChild());
        notifyChildUpdated(auth.familyId(), childId, result);
        return toVoidResponse(result);
    }

    @POST
    @Path("/update-nickname")
    @Operation(summary = "Rename the authenticated child profile")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Nickname updated",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
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

        OperationResult<Void> result = familyService.updateNickname(auth.familyId(), auth.childId(), request.nickname());
        notifyChildUpdated(auth.familyId(), auth.childId(), result);
        return toVoidResponse(result);
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
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
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
                .entity(ErrorResponse.of(BackendMessages.message("errors.friendIdRequired"), "BAD_REQUEST", 400))
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

    @GET
    @Path("/analytics")
    @Operation(summary = "Load analytics for the family or selected child")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Analytics snapshot returned",
            content = @Content(schema = @Schema(implementation = AnalyticsResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getAnalytics(@Context ContainerRequestContext ctx,
                                 @Parameter(description = "Requested analytics window", example = "month")
                                 @QueryParam("timeframe") @DefaultValue("month") String timeframe,
                                 @Parameter(description = "Optional child id override for admin sessions")
                                 @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;
        return toResponse(familyService.getAnalyticsData(auth.familyId(), effectiveChildId, timeframe));
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
        if (auth == null || !auth.isAdmin()) {
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

    @GET
    @Path("/history")
    @Operation(summary = "List history entries for a child")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "History page returned",
            content = @Content(schema = @Schema(implementation = PaginatedHistory.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getHistory(@Context ContainerRequestContext ctx,
                               @Parameter(description = "Optional child id override for admin sessions")
                               @QueryParam("childId") Integer childId,
                               @Parameter(description = "Page number", example = "1")
                               @QueryParam("page") @DefaultValue("1") int page,
                               @Parameter(description = "Page size", example = "20")
                               @QueryParam("limit") @DefaultValue("20") int limit) {
        var auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;
        if (effectiveChildId == null) {
            return badRequest(BackendMessages.message("errors.childIdRequired"));
        }

        return toResponse(familyService.getHistory(auth.familyId(), effectiveChildId, page, limit));
    }

    @GET
    @Path("/requests")
    @Operation(summary = "List purchase and task approval requests for the family")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Requests page returned",
            content = @Content(schema = @Schema(implementation = PaginatedRequests.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getRequests(@Context ContainerRequestContext ctx,
                                @Parameter(description = "Page number", example = "1")
                                @QueryParam("page") @DefaultValue("1") int page,
                                @Parameter(description = "Page size", example = "20")
                                @QueryParam("limit") @DefaultValue("20") int limit) {
        var auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        return toResponse(familyService.getRequests(auth.familyId(), page, limit));
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
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        if (request.key() == null || request.key().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(BackendMessages.message("errors.keyRequired"), "BAD_REQUEST", 400))
                .build();
        }

        return toVoidResponse(familyService.updatePreference(auth.familyId(), request.key(), request.value()));
    }

    private AuthContext getAuthOrFail(ContainerRequestContext ctx) {
        Object prop = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return prop instanceof AuthContext auth ? auth : null;
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
            .entity(ErrorResponse.unauthorized(BackendMessages.message("errors.unauthorized")))
            .build();
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.of(message, "BAD_REQUEST", 400))
            .build();
    }

    private <T> Response toResponse(OperationResult<T> result) {
        return switch (result) {
            case OperationResult.Success<T> s -> Response.ok(s.value()).build();
            case OperationResult.Failure<T> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(f.message(), errorCodeOrBadRequest(f.errorCode()), 400)).build();
        };
    }

    private Response toVoidResponse(OperationResult<Void> result) {
        return switch (result) {
            case OperationResult.Success<Void> ignored -> Response.ok(SimpleResponse.ok()).build();
            case OperationResult.Failure<Void> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(f.message(), errorCodeOrBadRequest(f.errorCode()), 400)).build();
        };
    }

    private String errorCodeOrBadRequest(String errorCode) {
        return errorCode != null ? errorCode : "BAD_REQUEST";
    }

    private void notifyDataUpdated(AuthContext auth, Integer childId, OperationResult<FamilyDataResponse> result) {
        if (!(result instanceof OperationResult.Success<FamilyDataResponse>)) {
            return;
        }

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("by", auth.role());
        if (childId != null) {
            payload.put("childId", childId);
        }
        webSocketNotificationService.notifyFamily(auth.familyId(), "DATA_UPDATED", payload);
    }

    private void notifyChildDeleted(String familyId, int childId, OperationResult<Void> result) {
        if (!(result instanceof OperationResult.Success<Void>)) {
            return;
        }
        webSocketNotificationService.notifyFamily(familyId, "CHILD_DELETED", Map.of("childId", childId));
    }

    private void notifyChildUpdated(String familyId, int childId, OperationResult<Void> result) {
        if (!(result instanceof OperationResult.Success<Void>)) {
            return;
        }
        webSocketNotificationService.notifyFamily(familyId, "CHILD_UPDATED", Map.of("childId", childId));
    }

    private void notifyChildUpdated(String familyId, OperationResult<ChildInfo> result,
                                    java.util.function.Function<ChildInfo, Integer> childIdExtractor) {
        if (!(result instanceof OperationResult.Success<ChildInfo> success)) {
            return;
        }
        Integer childId = childIdExtractor.apply(success.value());
        if (childId == null) {
            return;
        }
        webSocketNotificationService.notifyFamily(familyId, "CHILD_UPDATED", Map.of("childId", childId));
    }
}
