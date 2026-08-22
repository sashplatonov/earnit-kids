package com.sashplatonov.earnit.kids.identity.api.resource.auth;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.config.auth.JwtService;
import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.TokenResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class WsTokenResource {

    private final JwtService jwtService;

    @Inject
    public WsTokenResource(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GET
    @Path("/ws-token")
    public Response getWebSocketToken(@Context ContainerRequestContext ctx) {
        Object prop = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        if (!(prop instanceof AuthContext auth)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.unauthorized(BackendMessages.message("errors.unauthorized")))
                .build();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("familyId", auth.familyId());
        if (auth.childId() != null) {
            payload.put("childId", auth.childId());
        }
        payload.put("role", auth.role());

        String token = jwtService.signToken(payload, 60);
        return Response.ok(new TokenResponse(token)).build();
    }
}
