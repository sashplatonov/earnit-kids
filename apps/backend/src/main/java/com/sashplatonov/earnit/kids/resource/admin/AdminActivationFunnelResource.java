package com.sashplatonov.earnit.kids.resource.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminActivationFunnelResponse;
import com.sashplatonov.earnit.kids.service.admin.AdminActivationFunnelService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

@Path("/api/admin/analytics/activation-funnel")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminActivationFunnelResource {

    @Context
    SecurityContext securityContext;

    private final AdminActivationFunnelService service;

    public AdminActivationFunnelResource(AdminActivationFunnelService service) {
        this.service = service;
    }

    @GET
    public AdminActivationFunnelResponse getActivationFunnel() {
        if (!isAdmin(securityContext)) {
            throw new jakarta.ws.rs.ForbiddenException("Admin access required");
        }
        return service.getActivationFunnel();
    }

    private boolean isAdmin(SecurityContext ctx) {
        return ctx.isUserInRole("admin");
    }
}
