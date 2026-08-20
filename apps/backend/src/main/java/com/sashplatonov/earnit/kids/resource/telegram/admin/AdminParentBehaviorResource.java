package com.sashplatonov.earnit.kids.resource.telegram.admin;

import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;
import com.sashplatonov.earnit.kids.service.telegram.admin.AdminParentBehaviorService;
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
public class AdminParentBehaviorResource extends ResourceAuthSupport {

    private final AdminParentBehaviorService service;

    @Inject
    public AdminParentBehaviorResource(AdminParentBehaviorService service) {
        this.service = service;
    }

    @GET
    @Path("/parent-behavior")
    public Response getParentBehavior(
            @Context ContainerRequestContext ctx,
            @QueryParam("period") @DefaultValue("30d") String period) {
        requireAdmin(ctx);

        try {
            AdminParentBehaviorResponse response = service.getParentBehavior(AdminAnalyticsPeriod.parse(period));
            return Response.ok(response).build();
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

}
