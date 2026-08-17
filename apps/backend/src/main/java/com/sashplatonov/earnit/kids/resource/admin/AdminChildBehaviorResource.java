package com.sashplatonov.earnit.kids.resource.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminChildBehaviorResponse;
import com.sashplatonov.earnit.kids.service.admin.AdminChildBehaviorService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

@Path("/api/admin/analytics/child-behavior")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminChildBehaviorResource {

    @Context
    SecurityContext securityContext;

    private final AdminChildBehaviorService service;

    public AdminChildBehaviorResource(AdminChildBehaviorService service) {
        this.service = service;
    }

    @GET
    public AdminChildBehaviorResponse getChildBehavior(@QueryParam("period") String period) {
        if (!isAdmin(securityContext)) {
            throw new jakarta.ws.rs.ForbiddenException("Admin access required");
        }
        return service.getChildBehavior(period);
    }

    private boolean isAdmin(SecurityContext ctx) {
        return ctx.isUserInRole("admin");
    }
}
