package com.sashplatonov.earnit.kids.resource.family;

import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.dto.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.dto.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.service.database.BaseDataService;
import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.OperationResultResponses;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.function.Supplier;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Family dashboard read endpoints")
public class FamilyReadResource extends ResourceAuthSupport {

    private static final Logger LOG = Logger.getLogger(FamilyReadResource.class);
    private final Supplier<FamilyService> familyService;
    private final BaseDataService baseDataService;

    public FamilyReadResource(FamilyService familyService, BaseDataService baseDataService) {
        this.familyService = () -> familyService;
        this.baseDataService = baseDataService;
    }

    @Inject
    public FamilyReadResource(Provider<FamilyService> familyService, BaseDataService baseDataService) {
        this.familyService = familyService::get;
        this.baseDataService = baseDataService;
    }

    @GET
    @Path("/data")
    @Operation(summary = "Load the dashboard shell payload for a family or child session")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Dashboard shell returned",
            content = @Content(schema = @Schema(implementation = FamilyDashboardShellResponse.class))),
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
        LOG.infof("GET /api/data: role=%s, isAdmin=%s, familyId=%s, email=%s, childId=%s",
            auth.role(), auth.isAdmin(), auth.familyId(), auth.email(), effectiveChildId);
        OperationResult<FamilyDashboardShellResponse> result =
            familyService.get().loadFamilyShellData(auth.familyId(), effectiveChildId, auth.isAdmin());

        return OperationResultResponses.toOk(result);
    }

    @GET
    @Path("/data/details")
    @Operation(summary = "Load the heavy dashboard details for a family or child session")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Dashboard details returned",
            content = @Content(schema = @Schema(implementation = FamilyDashboardDetailResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getFamilyDataDetails(@Context ContainerRequestContext ctx,
                                         @Parameter(description = "Child id override for admin sessions")
                                         @QueryParam("childId") Integer childId) {
        var auth = getAuthOrFail(ctx);
        if (auth == null) {
            return unauthorized();
        }

        Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;
        OperationResult<FamilyDashboardDetailResponse> result =
            familyService.get().loadFamilyDetailData(auth.familyId(), effectiveChildId, auth.isAdmin());

        return OperationResultResponses.toOk(result);
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
        return OperationResultResponses.toOk(familyService.get().getAnalyticsData(auth.familyId(), effectiveChildId, timeframe));
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

        return OperationResultResponses.toOk(familyService.get().getHistory(auth.familyId(), effectiveChildId, page, limit));
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
        if (auth == null || !auth.canEditFamilyData()) {
            return unauthorized();
        }

        return OperationResultResponses.toOk(familyService.get().getRequests(auth.familyId(), page, limit));
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

}
