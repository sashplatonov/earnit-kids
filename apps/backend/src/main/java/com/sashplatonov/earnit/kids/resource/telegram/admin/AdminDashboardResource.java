package com.sashplatonov.earnit.kids.resource.telegram.admin;

import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.dto.response.AdminDashboardResponse;
import com.sashplatonov.earnit.kids.service.telegram.admin.AdminDashboardService;
import com.sashplatonov.earnit.kids.service.telegram.admin.AdminAnalyticsPeriod;
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
public class AdminDashboardResource extends ResourceAuthSupport {

    private final AdminDashboardService dashboardService;

    @Inject
    public AdminDashboardResource(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GET
    public Response getDashboard(
            @Context ContainerRequestContext ctx,
            @QueryParam("period") @DefaultValue("30d") String period) {
        requireAdmin(ctx);

        try {
            AdminAnalyticsPeriod analyticsPeriod = AdminAnalyticsPeriod.parse(period);
            AdminDashboardResponse response = dashboardService.getDashboard(analyticsPeriod);
            return Response.ok(response).build();
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

}
