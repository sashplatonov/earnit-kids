package com.sashplatonov.earnit.kids.resource.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;
import com.sashplatonov.earnit.kids.service.admin.AdminParentBehaviorService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

@Path("/api/admin/analytics/parent-behavior")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminParentBehaviorResource {

    @Context
    SecurityContext securityContext;

    private final AdminParentBehaviorService service;

    public AdminParentBehaviorResource(AdminParentBehaviorService service) {
        this.service = service;
    }

    @GET
    public AdminParentBehaviorResponse getParentBehavior(@QueryParam("period") String period) {
        if (!isAdmin(securityContext)) {
            throw new jakarta.ws.rs.ForbiddenException("Admin access required");
        }
        return service.getParentBehavior(period);
    }

    private boolean isAdmin(SecurityContext ctx) {
        return ctx.isUserInRole("admin");
    }
}
