package com.sashplatonov.earnit.kids.resource.admin;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.AdminRetentionResponse;
import com.sashplatonov.earnit.kids.service.admin.AdminRetentionService;
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
public class AdminRetentionResource {

    private final AdminRetentionService service;

    @Inject
    public AdminRetentionResource(AdminRetentionService service) {
        this.service = service;
    }

    @GET
    @Path("/retention")
    public Response getRetention(
            @Context ContainerRequestContext ctx,
            @QueryParam("period") @DefaultValue("30d") String period) {
        Response authFailure = requireAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }

        AdminRetentionResponse response = service.getRetention(period);
        return Response.ok(response).build();
    }

    private Response requireAdmin(ContainerRequestContext ctx) {
        AuthContext auth = auth(ctx);
        if (auth == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Unauthorized\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        if (!auth.isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"Admin access required\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        return null;
    }

    private AuthContext auth(ContainerRequestContext ctx) {
        Object property = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return property instanceof AuthContext auth ? auth : null;
    }
}
