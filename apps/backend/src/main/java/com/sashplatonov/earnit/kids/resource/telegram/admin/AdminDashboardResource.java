package com.sashplatonov.earnit.kids.resource.telegram.admin;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.AdminDashboardResponse;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.service.telegram.admin.AdminDashboardService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/admin/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin Analytics", description = "Admin dashboard analytics endpoints")
public class AdminDashboardResource {

    private final AdminDashboardService dashboardService;

    @Inject
    public AdminDashboardResource(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GET
    public Response getDashboard(
            @Context ContainerRequestContext ctx,
            @QueryParam("period") @DefaultValue("30d") String period) {
        Response authFailure = requireAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }

        AdminDashboardResponse response = dashboardService.getDashboard(period);
        return Response.ok(response).build();
    }

    private Response requireAdmin(ContainerRequestContext ctx) {
        AuthContext auth = auth(ctx);
        if (auth == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.unauthorized(BackendMessages.message("errors.unauthorized")))
                .build();
        }
        if (!auth.isAdmin()) {
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
