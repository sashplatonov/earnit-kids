package com.sashplatonov.earnit.kids.resource.system;

import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.service.common.PageRequest;
import com.sashplatonov.earnit.kids.service.system.SystemDashboardService;
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
public class SystemDashboardResource extends ResourceAuthSupport {

    private final SystemDashboardService systemDashboardService;

    @Inject
    public SystemDashboardResource(SystemDashboardService systemDashboardService) {
        this.systemDashboardService = systemDashboardService;
    }

    @GET
    @Path("/system/overview")
    public Response getSystemOverview(@Context ContainerRequestContext ctx) {
        Response authFailure = requireSuperAdminResponse(ctx);
        if (authFailure != null) {
            return authFailure;
        }
        return Response.ok(systemDashboardService.getOverview()).build();
    }

    @GET
    @Path("/system/db")
    public Response getDatabaseHealth(@Context ContainerRequestContext ctx) {
        Response authFailure = requireSuperAdminResponse(ctx);
        if (authFailure != null) {
            return authFailure;
        }
        return Response.ok(systemDashboardService.getDbHealth()).build();
    }

    @GET
    @Path("/system/http-metrics")
    public Response getHttpMetrics(@Context ContainerRequestContext ctx) {
        Response authFailure = requireSuperAdminResponse(ctx);
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
        Response authFailure = requireSuperAdminResponse(ctx);
        if (authFailure != null) {
            return authFailure;
        }
        int resolvedLimit = PageRequest.of(1, limit == null ? 100 : limit, 500).limit();
        String resolvedLevel = level == null || level.isBlank() ? "all" : level;
        return Response.ok(systemDashboardService.getLogs(resolvedLevel, resolvedLimit)).build();
    }

}
