package com.sashplatonov.earnit.kids.family.api.resource;

import com.sashplatonov.earnit.kids.family.application.invitation.ParentInvitationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;

@Path("/invite/parent")
@Produces(MediaType.APPLICATION_JSON)
public class ParentInvitationEntryResource {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    ParentInvitationService invitationService;

    @GET
    @Path("/{token}")
    public Response enter(@PathParam("token") String token, @Context ContainerRequestContext request) {
        String binding = randomBinding();
        var result = invitationService.begin(token, binding, "/invite/parent");
        if (!(result instanceof OperationResult.Success<ParentInvitationService.Continuation> success)) {
            return Response.status(Response.Status.NOT_FOUND).header("Cache-Control", "no-store").build();
        }
        var continuation = success.value();
        return Response.seeOther(URI.create("/invite/parent"))
            .header("Cache-Control", "no-store")
            .header("Set-Cookie", "invite_flow=" + binding + "; Max-Age=600; Path=/; HttpOnly; SameSite=Lax")
            .header("Set-Cookie", "invite_continuation=" + continuation.id()
                + "; Max-Age=600; Path=/; HttpOnly; SameSite=Lax")
            .build();
    }

    @POST
    @Path("/accept")
    public Response accept(@CookieParam("invite_flow") String binding,
                           @CookieParam("invite_continuation") Integer continuationId,
                           @Context ContainerRequestContext request) {
        var auth = request.getProperty(com.sashplatonov.earnit.kids.config.auth.AuthFilter.AUTH_CONTEXT_PROPERTY);
        String email = auth instanceof com.sashplatonov.earnit.kids.config.auth.AuthContext context
            ? context.email() : null;
        if (binding == null || continuationId == null || email == null) {
            return Response.status(Response.Status.UNAUTHORIZED).header("Cache-Control", "no-store").build();
        }
        var result = invitationService.accept(continuationId, binding, email);
        return result.isSuccess()
            ? Response.ok().header("Cache-Control", "no-store").build()
            : Response.status(Response.Status.BAD_REQUEST).header("Cache-Control", "no-store").build();
    }

    private String randomBinding() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
