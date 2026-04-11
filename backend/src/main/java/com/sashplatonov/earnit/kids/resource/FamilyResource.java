package com.sashplatonov.earnit.kids.resource;

import jakarta.inject.Inject;
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
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.service.BaseDataService;
import com.sashplatonov.earnit.kids.service.FamilyService;
import com.sashplatonov.earnit.kids.config.AuthContext;

import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FamilyResource {

    private final FamilyService familyService;
    private final BaseDataService baseDataService;

    @Inject
    public FamilyResource(FamilyService familyService,
                          BaseDataService baseDataService) {
        this.familyService = familyService;
        this.baseDataService = baseDataService;
    }

    @GET
    @Path("/data")
    public Response getFamilyData(@Context ContainerRequestContext ctx,
                                  @QueryParam("childId") Integer childId) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        Integer effectiveChildId = childId != null ? childId : auth.childId();
        OperationResult<FamilyDataResponse> result =
            familyService.loadFamilyData(auth.familyId(), effectiveChildId);

        return toResponse(result);
    }

    @POST
    @Path("/data")
    public Response saveFamilyData(@Context ContainerRequestContext ctx,
                                   Map<String, Object> payload) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        Integer childId = payload.get("childId") instanceof Number n ? n.intValue() : auth.childId();
        OperationResult<FamilyDataResponse> result =
            familyService.saveFamilyData(auth.familyId(), childId, payload);

        return toResponse(result);
    }

    @GET
    @Path("/base-data")
    public Response getBaseData(@Context ContainerRequestContext ctx) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        return Response.ok(baseDataService.getBaseData()).build();
    }

    @POST
    @Path("/children")
    public Response createChild(@Context ContainerRequestContext ctx, Map<String, String> body) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        String name = body != null ? body.get("name") : null;
        OperationResult<ChildInfo> result = familyService.createChild(auth.familyId(), name);

        return switch (result) {
            case OperationResult.Success<ChildInfo> s ->
                Response.status(Response.Status.CREATED).entity(s.value()).build();
            case OperationResult.Failure<ChildInfo> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(f.message())).build();
        };
    }

    @DELETE
    @Path("/children/{childId}")
    public Response deleteChild(@Context ContainerRequestContext ctx,
                                @PathParam("childId") int childId) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        OperationResult<Void> result = familyService.deleteChild(auth.familyId(), childId);
        return toVoidResponse(result);
    }

    @PUT
    @Path("/children/{childId}/nickname")
    public Response updateNickname(@Context ContainerRequestContext ctx,
                                   @PathParam("childId") int childId,
                                   Map<String, String> body) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        String name = body != null ? body.get("name") : null;
        OperationResult<Void> result = familyService.updateNickname(auth.familyId(), childId, name);
        return toVoidResponse(result);
    }

    @PUT
    @Path("/children/{childId}/settings")
    public Response updateChildSettings(@Context ContainerRequestContext ctx,
                                        @PathParam("childId") int childId,
                                        Map<String, Object> body) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        String name = body.get("name") instanceof String s ? s : "";
        int dailyCoinLimit = readInt(body, "dailyCoinLimit", readInt(body, "daily_coin_limit", 0));
        int monthlyLimit = readInt(body, "monthlyLimit", readInt(body, "monthly_limit", 10000));

        OperationResult<Void> result = familyService.updateChildSettings(
            auth.familyId(), childId, name, dailyCoinLimit, monthlyLimit);
        return toVoidResponse(result);
    }

    @POST
    @Path("/children/{childId}/settings")
    public Response updateChildSettingsPost(@Context ContainerRequestContext ctx,
                                            @PathParam("childId") int childId,
                                            Map<String, Object> body) {
        return updateChildSettings(ctx, childId, body);
    }

    @PUT
    @Path("/children/{childId}/theme")
    public Response updateChildTheme(@Context ContainerRequestContext ctx,
                                     @PathParam("childId") int childId,
                                     Map<String, String> body) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        String theme = body != null ? body.get("theme") : null;
        OperationResult<Void> result = familyService.updateChildTheme(childId, theme);
        return toVoidResponse(result);
    }

    @POST
    @Path("/children/{childId}/theme")
    public Response updateChildThemePost(@Context ContainerRequestContext ctx,
                                         @PathParam("childId") int childId,
                                         Map<String, String> body) {
        return updateChildTheme(ctx, childId, body);
    }

    @POST
    @Path("/update-nickname")
    public Response updateOwnNickname(@Context ContainerRequestContext ctx,
                                      Map<String, String> body) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isChild() || auth.childId() == null) {
            return unauthorized();
        }

        String nickname = body != null ? body.get("nickname") : null;
        OperationResult<Void> result = familyService.updateNickname(auth.familyId(), auth.childId(), nickname);
        return toVoidResponse(result);
    }

    @GET
    @Path("/search-user")
    public Response searchUser(@Context ContainerRequestContext ctx,
                               @QueryParam("nickname") String nickname) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isChild() || auth.childId() == null) {
            return unauthorized();
        }

        OperationResult<?> result = familyService.searchByNickname(nickname, auth.childId());
        return toResponse(result);
    }

    @POST
    @Path("/add-friend")
    public Response addFriend(@Context ContainerRequestContext ctx,
                              Map<String, Object> body) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isChild() || auth.childId() == null) {
            return unauthorized();
        }

        int friendId = readInt(body, "friendId", 0);
        if (friendId <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(SimpleResponse.error("friendId is required"))
                .build();
        }

        OperationResult<Void> result = familyService.addFriend(auth.familyId(), auth.childId(), friendId);
        return toVoidResponse(result);
    }

    @GET
    @Path("/friends-list")
    public Response getFriendsList(@Context ContainerRequestContext ctx) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isChild() || auth.childId() == null) {
            return unauthorized();
        }

        OperationResult<?> result = familyService.getFriendsData(auth.childId());
        return toResponse(result);
    }

    @GET
    @Path("/analytics")
    public Response getAnalytics(@Context ContainerRequestContext ctx,
                                 @QueryParam("timeframe") @DefaultValue("month") String timeframe,
                                 @QueryParam("childId") Integer childId) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;
        OperationResult<?> result = familyService.getAnalyticsData(auth.familyId(), effectiveChildId, timeframe);
        return toResponse(result);
    }

    @GET
    @Path("/children/{childId}/link")
    public Response getChildLink(@Context ContainerRequestContext ctx,
                                 @PathParam("childId") int childId) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        OperationResult<String> result = familyService.getChildLoginLink(childId);
        return switch (result) {
            case OperationResult.Success<String> s -> Response.ok(Map.of("token", s.value())).build();
            case OperationResult.Failure<String> f ->
                Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.of(f.message())).build();
        };
    }

    @POST
    @Path("/children/{childId}/regenerate-token")
    public Response regenerateChildToken(@Context ContainerRequestContext ctx,
                                         @PathParam("childId") int childId) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null || !auth.isAdmin()) {
            return unauthorized();
        }

        OperationResult<String> result = familyService.regenerateChildToken(childId);
        return switch (result) {
            case OperationResult.Success<String> s -> Response.ok(Map.of("token", s.value())).build();
            case OperationResult.Failure<String> f ->
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.of(f.message())).build();
        };
    }

    @GET
    @Path("/history")
    public Response getHistory(@Context ContainerRequestContext ctx,
                               @QueryParam("childId") Integer childId,
                               @QueryParam("page") @DefaultValue("1") int page,
                               @QueryParam("limit") @DefaultValue("20") int limit) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        int effectiveChildId = childId != null ? childId : (auth.childId() != null ? auth.childId() : 0);
        OperationResult<PaginatedHistory> result = familyService.getHistory(effectiveChildId, page, limit);
        return toResponse(result);
    }

    @GET
    @Path("/requests")
    public Response getRequests(@Context ContainerRequestContext ctx,
                                @QueryParam("page") @DefaultValue("1") int page,
                                @QueryParam("limit") @DefaultValue("20") int limit) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        OperationResult<PaginatedRequests> result = familyService.getRequests(auth.familyId(), page, limit);
        return toResponse(result);
    }

    @POST
    @Path("/preferences")
    public Response updatePreference(@Context ContainerRequestContext ctx,
                                     Map<String, Object> body) {
        AuthContext auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        String key = body.get("key") instanceof String s ? s : null;
        Object value = body.get("value");
        if (key == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of("key is required")).build();
        }

        OperationResult<Void> result = familyService.updatePreference(auth.familyId(), key, value);
        return toVoidResponse(result);
    }

    // ------ helpers ------

    private AuthContext getAuthOrFail(ContainerRequestContext ctx) {
        Object prop = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return prop instanceof AuthContext ac ? ac : null;
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
            .entity(ErrorResponse.of("Unauthorized", "UNAUTHORIZED", 401))
            .build();
    }

    private <T> Response toResponse(OperationResult<T> result) {
        return switch (result) {
            case OperationResult.Success<T> s -> Response.ok(s.value()).build();
            case OperationResult.Failure<T> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(f.message())).build();
        };
    }

    private Response toVoidResponse(OperationResult<Void> result) {
        return switch (result) {
            case OperationResult.Success<Void> _ -> Response.ok(SimpleResponse.ok()).build();
            case OperationResult.Failure<Void> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(SimpleResponse.error(f.message())).build();
        };
    }

    private int readInt(Map<String, ?> body, String key, int fallback) {
        if (body == null) {
            return fallback;
        }
        Object value = body.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
