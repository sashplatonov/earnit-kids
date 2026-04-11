package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/push")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PushResource {

    @POST
    @Path("/register")
    public Response register(@Context ContainerRequestContext ctx) {
        AuthContext auth = getAuth(ctx);
        if (auth == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(SimpleResponse.error("Unauthorized"))
                .build();
        }

        return Response.ok(SimpleResponse.ok()).build();
    }

    @POST
    @Path("/unregister")
    public Response unregister(@Context ContainerRequestContext ctx) {
        AuthContext auth = getAuth(ctx);
        if (auth == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(SimpleResponse.error("Unauthorized"))
                .build();
        }

        return Response.ok(SimpleResponse.ok()).build();
    }

    private AuthContext getAuth(ContainerRequestContext ctx) {
        Object prop = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return prop instanceof AuthContext auth ? auth : null;
    }
}
