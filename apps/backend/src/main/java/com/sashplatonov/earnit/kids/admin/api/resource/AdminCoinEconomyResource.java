package com.sashplatonov.earnit.kids.admin.api.resource;

import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.admin.application.AdminCoinEconomyService;
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
public class AdminCoinEconomyResource extends ResourceAuthSupport {

    private final AdminCoinEconomyService coinEconomyService;

    @Inject
    public AdminCoinEconomyResource(AdminCoinEconomyService coinEconomyService) {
        this.coinEconomyService = coinEconomyService;
    }

    @GET
    @Path("/coin-economy")
    public Response getCoinEconomy(
            @Context ContainerRequestContext ctx,
            @QueryParam("period") @DefaultValue("30d") String period) {
        requireAdmin(ctx);

        try {
            AdminCoinEconomyResponse response = coinEconomyService.getCoinEconomy(AdminAnalyticsPeriod.parse(period));
            return Response.ok(response).build();
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

}
