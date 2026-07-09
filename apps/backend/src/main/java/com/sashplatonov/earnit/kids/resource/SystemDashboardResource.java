package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.service.SystemDashboardService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/super")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "System", description = "System dashboard endpoints")
public class SystemDashboardResource {

    private final SystemDashboardService systemDashboardService;

    @Inject
    public SystemDashboardResource(SystemDashboardService systemDashboardService) {
        this.systemDashboardService = systemDashboardService;
    }

    @GET
    @Path("/system/overview")
    public Response getSystemOverview(@Context ContainerRequestContext ctx) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }
        return Response.ok(systemDashboardService.getOverview()).build();
    }

    @GET
    @Path("/system/db")
    public Response getDatabaseHealth(@Context ContainerRequestContext ctx) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }
        return Response.ok(systemDashboardService.getDbHealth()).build();
    }

    @GET
    @Path("/system/http-metrics")
    public Response getHttpMetrics(@Context ContainerRequestContext ctx) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }
        return Response.ok(systemDashboardService.getHttpMetrics()).build();
    }

    @GET
    @Path("/system/logs")
    public Response getLogs(@Context ContainerRequestContext ctx,
                            @QueryParam("level") String level,
                            @QueryParam("limit") Integer limit) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }
        int resolvedLimit = limit == null ? 100 : Math.max(1, Math.min(limit, 500));
        String resolvedLevel = level == null || level.isBlank() ? "all" : level;
        return Response.ok(systemDashboardService.getLogs(resolvedLevel, resolvedLimit)).build();
    }

    private Response requireSuperAdmin(ContainerRequestContext ctx) {
        AuthContext auth = auth(ctx);
        if (auth == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.unauthorized(BackendMessages.message("errors.unauthorized")))
                .build();
        }
        if (!auth.isSuperAdmin()) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(ErrorResponse.of(BackendMessages.message("errors.forbidden"), "FORBIDDEN", 403))
                .build();
        }
        return null;
    }

    private AuthContext auth(ContainerRequestContext ctx) {
        Object property = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return property instanceof AuthContext auth ? auth : null;
    }
}
