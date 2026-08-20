package com.sashplatonov.earnit.kids.resource.telegram.admin;

import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.dto.response.AdminActivationFunnelResponse;
import com.sashplatonov.earnit.kids.service.telegram.admin.AdminActivationFunnelService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/admin/analytics")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin Analytics", description = "Admin dashboard analytics endpoints")
public class AdminActivationFunnelResource extends ResourceAuthSupport {

    private final AdminActivationFunnelService service;

    @Inject
    public AdminActivationFunnelResource(AdminActivationFunnelService service) {
        this.service = service;
    }

    @GET
    @Path("/activation-funnel")
    public Response getActivationFunnel(@Context ContainerRequestContext ctx) {
        requireAdmin(ctx);

        AdminActivationFunnelResponse response = service.getActivationFunnel();
        return Response.ok(response).build();
    }

}
