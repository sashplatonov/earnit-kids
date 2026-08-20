package com.sashplatonov.earnit.kids.resource.telegram.admin;

import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.dto.response.AdminTrendsResponse;
import com.sashplatonov.earnit.kids.service.telegram.admin.AdminTrendsService;
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

@Path("/api/admin/analytics")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin Analytics", description = "Admin dashboard analytics endpoints")
public class AdminTrendsResource extends ResourceAuthSupport {

    private final AdminTrendsService service;

    @Inject
    public AdminTrendsResource(AdminTrendsService service) {
        this.service = service;
    }

    @GET
    @Path("/trends")
    public Response getTrends(
            @Context ContainerRequestContext ctx,
            @QueryParam("period") @DefaultValue("30d") String period) {
        requireAdmin(ctx);

        try {
            AdminAnalyticsPeriod analyticsPeriod = AdminAnalyticsPeriod.parse(period);
            AdminTrendsResponse response = service.getTrends(analyticsPeriod);
            return Response.ok(response).build();
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

}
