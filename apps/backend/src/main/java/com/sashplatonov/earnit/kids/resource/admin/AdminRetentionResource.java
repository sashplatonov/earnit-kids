package com.sashplatonov.earnit.kids.resource.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminRetentionResponse;
import com.sashplatonov.earnit.kids.service.admin.AdminRetentionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

@Path("/api/admin/analytics/retention")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminRetentionResource {

    @Context
    SecurityContext securityContext;

    private final AdminRetentionService service;

    public AdminRetentionResource(AdminRetentionService service) {
        this.service = service;
    }

    @GET
    public AdminRetentionResponse getRetention(@QueryParam("period") @DefaultValue("30d") String period) {
        if (!isAdmin(securityContext)) {
            throw new jakarta.ws.rs.ForbiddenException("Admin access required");
        }
        return service.getRetention(period);
    }

    private boolean isAdmin(SecurityContext ctx) {
        return ctx.isUserInRole("admin");
    }
}
