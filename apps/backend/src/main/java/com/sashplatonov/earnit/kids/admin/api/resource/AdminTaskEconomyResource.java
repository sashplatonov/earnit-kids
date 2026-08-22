package com.sashplatonov.earnit.kids.admin.api.resource;

import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import com.sashplatonov.earnit.kids.admin.application.AdminTaskEconomyService;
import com.sashplatonov.earnit.kids.admin.application.AdminAnalyticsPeriod;
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

@Path("/api/admin/analytics")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin Analytics", description = "Admin dashboard analytics endpoints")
public class AdminTaskEconomyResource extends ResourceAuthSupport {

    private final AdminTaskEconomyService taskEconomyService;

    @Inject
    public AdminTaskEconomyResource(AdminTaskEconomyService taskEconomyService) {
        this.taskEconomyService = taskEconomyService;
    }

    @GET
    @Path("/task-economy")
    public Response getTaskEconomy(
            @Context ContainerRequestContext ctx,
            @QueryParam("period") @DefaultValue("30d") String period) {
        requireAdmin(ctx);

        try {
            AdminTasksResponse response = taskEconomyService.getTaskEconomy(AdminAnalyticsPeriod.parse(period));
            return Response.ok(response).build();
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

}
