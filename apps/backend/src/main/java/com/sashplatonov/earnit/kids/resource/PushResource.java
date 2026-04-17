package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
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
@Tag(name = "Push", description = "Push-notification subscription placeholders")
public class PushResource {

    @POST
    @Path("/register")
    @Operation(summary = "Register the current session for push notifications")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Push registration accepted",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response register(@Context ContainerRequestContext ctx) {
        AuthContext auth = getAuth(ctx);
        if (auth == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.unauthorized("Unauthorized"))
                .build();
        }

        return Response.ok(SimpleResponse.ok()).build();
    }

    @POST
    @Path("/unregister")
    @Operation(summary = "Unregister the current session from push notifications")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Push registration removed",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response unregister(@Context ContainerRequestContext ctx) {
        AuthContext auth = getAuth(ctx);
        if (auth == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.unauthorized("Unauthorized"))
                .build();
        }

        return Response.ok(SimpleResponse.ok()).build();
    }

    private AuthContext getAuth(ContainerRequestContext ctx) {
        Object prop = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return prop instanceof AuthContext auth ? auth : null;
    }
}
