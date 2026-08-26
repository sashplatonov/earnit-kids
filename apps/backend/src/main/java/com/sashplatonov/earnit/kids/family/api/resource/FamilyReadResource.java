package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.api.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.family.api.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.family.api.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.family.api.request.UpdateFamilyLocaleRequest;
import com.sashplatonov.earnit.kids.family.api.response.FamilyLocaleResponse;
import com.sashplatonov.earnit.kids.family.application.catalog.LocalizedCatalogService;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.i18n.BackendLocaleSupport;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.OperationResultResponses;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.validation.Valid;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Family", description = "Family dashboard read endpoints")
public class FamilyReadResource extends ResourceAuthSupport {

  private static final Logger LOG = Logger.getLogger(FamilyReadResource.class);
  private final Supplier<FamilyService> familyService;
  private final LocalizedCatalogService localizedCatalogService;

  @Inject
  FamilyRepository familyRepository;

  @Inject
  public FamilyReadResource(FamilyService familyService, LocalizedCatalogService localizedCatalogService) {
    this.familyService = () -> familyService;
    this.localizedCatalogService = localizedCatalogService;
  }

  @GET
  @Path("/data")
  @Operation(summary = "Load the dashboard shell payload for a family or child session")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Dashboard shell returned",
        content = @Content(schema = @Schema(implementation = FamilyDashboardShellResponse.class))),
    @APIResponse(
        responseCode = "401",
        description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Response getFamilyData(
      @Context ContainerRequestContext ctx,
      @Parameter(description = "Child id override for admin sessions") @QueryParam("childId")
          Integer childId) {
    var auth = getAuthOrFail(ctx);
    if (auth == null) {
      return unauthorized();
    }

    Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;
    LOG.infof(
        "GET /api/data: role=%s, isAdmin=%s, familyId=%s, email=%s, childId=%s",
        auth.role(), auth.isAdmin(), auth.familyId(), auth.email(), effectiveChildId);
    OperationResult<FamilyDashboardShellResponse> result =
        familyService.get().loadFamilyShellData(auth.familyId(), effectiveChildId, auth.isAdmin());

    return OperationResultResponses.toOk(result);
  }

  @GET
  @Path("/family/locale")
  @Operation(summary = "Read the active family's language")
  public Response getFamilyLocale(@Context ContainerRequestContext ctx) {
    var auth = getAuthOrFail(ctx);
    if (auth == null) {
      return unauthorized();
    }
    return familyRepository.findById(auth.familyId())
        .map(family -> Response.ok(toLocaleResponse(family, auth.isFamilyAdmin())).build())
        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
  }

  @PUT
  @Path("/family/locale")
  @Operation(summary = "Set the active family's language")
  public Response updateFamilyLocale(
      @Context ContainerRequestContext ctx, @Valid UpdateFamilyLocaleRequest request) {
    var auth = getAuthOrFail(ctx);
    if (auth == null || !auth.isFamilyAdmin()) {
      return unauthorized();
    }
    String requestedLocale = request == null ? null : request.locale();
    String normalizedLocale = BackendLocaleSupport.normalizeLocale(requestedLocale) == null
        ? null
        : BackendLocaleSupport.toLanguageTag(BackendLocaleSupport.normalizeLocale(requestedLocale));
    FamilyLocale locale = FamilyLocale.fromLanguageTag(normalizedLocale);
    if (locale == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ErrorResponse.of(
              "Unsupported locale", "UNSUPPORTED_LOCALE", 400, Map.of("field", "locale"), null))
          .build();
    }
    if (!familyRepository.updateLocale(auth.familyId(), locale)) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return familyRepository.findById(auth.familyId())
        .map(family -> Response.ok(toLocaleResponse(family, true)).build())
        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
  }

  private static FamilyLocaleResponse toLocaleResponse(FamilyEntity family, boolean familyAdmin) {
    FamilyLocale locale = family.getLocale();
    return new FamilyLocaleResponse(locale == null ? "en" : locale.name(), familyAdmin && locale == null);
  }

  @GET
  @Path("/data/details")
  @Operation(summary = "Load the heavy dashboard details for a family or child session")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Dashboard details returned",
        content = @Content(schema = @Schema(implementation = FamilyDashboardDetailResponse.class))),
    @APIResponse(
        responseCode = "401",
        description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Response getFamilyDataDetails(
      @Context ContainerRequestContext ctx,
      @Parameter(description = "Child id override for admin sessions") @QueryParam("childId")
          Integer childId) {
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
    @APIResponse(
        responseCode = "200",
        description = "Analytics snapshot returned",
        content = @Content(schema = @Schema(implementation = AnalyticsResponse.class))),
    @APIResponse(
        responseCode = "401",
        description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Response getAnalytics(
      @Context ContainerRequestContext ctx,
      @Parameter(description = "Requested analytics window", example = "month")
          @QueryParam("timeframe")
          @DefaultValue("month")
          String timeframe,
      @Parameter(description = "Optional child id override for admin sessions")
          @QueryParam("childId")
          Integer childId) {
    var auth = getAuthOrFail(ctx);
    if (auth == null) {
      return unauthorized();
    }

    Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;
    return OperationResultResponses.toOk(
        familyService.get().getAnalyticsData(auth.familyId(), effectiveChildId, timeframe));
  }

  @GET
  @Path("/history")
  @Operation(summary = "List history entries for a child")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "History page returned",
        content = @Content(schema = @Schema(implementation = PaginatedHistory.class))),
    @APIResponse(
        responseCode = "401",
        description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Response getHistory(
      @Context ContainerRequestContext ctx,
      @Parameter(description = "Optional child id override for admin sessions")
          @QueryParam("childId")
          Integer childId,
      @Parameter(description = "Page number", example = "1") @QueryParam("page") @DefaultValue("1")
          int page,
      @Parameter(description = "Page size", example = "20") @QueryParam("limit") @DefaultValue("20")
          int limit) {
    var auth = getAuthOrFail(ctx);
    if (auth == null) {
      return unauthorized();
    }

    Integer effectiveChildId = auth.isChild() ? auth.childId() : childId;
    if (effectiveChildId == null) {
      return badRequest(BackendMessages.message("errors.childIdRequired"));
    }

    return OperationResultResponses.toOk(
        familyService.get().getHistory(auth.familyId(), effectiveChildId, page, limit));
  }

  @GET
  @Path("/requests")
  @Operation(summary = "List purchase and task approval requests for the family")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Requests page returned",
        content = @Content(schema = @Schema(implementation = PaginatedRequests.class))),
    @APIResponse(
        responseCode = "401",
        description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Response getRequests(
      @Context ContainerRequestContext ctx,
      @Parameter(description = "Page number", example = "1") @QueryParam("page") @DefaultValue("1")
          int page,
      @Parameter(description = "Page size", example = "20") @QueryParam("limit") @DefaultValue("20")
          int limit) {
    var auth = getAuthOrFail(ctx);
    if (auth == null || !auth.canEditFamilyData()) {
      return unauthorized();
    }

    return OperationResultResponses.toOk(
        familyService.get().getRequests(auth.familyId(), page, limit));
  }

  @GET
  @Path("/base-data")
  @Operation(summary = "Load the static task and reward catalog")
  @APIResponses({
    @APIResponse(responseCode = "200", description = "Base catalog returned"),
    @APIResponse(
        responseCode = "401",
        description = "Authentication required",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Response getBaseData(@Context ContainerRequestContext ctx) {
    var auth = getAuthOrFail(ctx);
    if (auth == null) {
      return unauthorized();
    }

    var family = familyRepository.findById(auth.familyId());
    if (family.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    FamilyLocale locale = family.get().getLocale();
    if (locale == null) {
      locale = FamilyLocale.en;
    }
    return Response.ok(localizedCatalogService.getBaseData(locale)).build();
  }
}
