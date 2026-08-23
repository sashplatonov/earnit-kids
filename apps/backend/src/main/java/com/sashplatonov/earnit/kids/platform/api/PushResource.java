package com.sashplatonov.earnit.kids.platform.api;

import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import com.sashplatonov.earnit.kids.shared.api.response.SimpleResponse;
import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.platform.webpush.WebPushService;
import com.sashplatonov.earnit.kids.platform.webpush.WebPushSubscriptionRequest;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/push")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Push", description = "Browser push-notification subscriptions")
public class PushResource extends ResourceAuthSupport {
    private WebPushService service;

    public PushResource() { }

    @jakarta.inject.Inject
    public PushResource(WebPushService service) { this.service = service; }

    @GET
    @Path("/vapid-public-key")
    @Operation(summary = "Read the public VAPID key for browser subscription")
    public Response vapidPublicKey(@Context ContainerRequestContext ctx) {
        Response authFailure = requireAuthResponse(ctx);
        if (authFailure != null) return authFailure;
        return service.publicVapidKey()
            .map(key -> Response.ok(new WebPushPublicKeyResponse(key))
                .header("Cache-Control", "no-store").build())
            .orElseGet(() -> Response.noContent().header("Cache-Control", "no-store").build());
    }

    // EXPLAIN: Keep the authentication-only overload for focused resource tests.
    public Response register(@Context ContainerRequestContext ctx) {
        return register(null, ctx);
    }

    @POST
    @Path("/register")
    @Operation(summary = "Register the current session for push notifications")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Push registration accepted",
            content = @Content(schema = @Schema(implementation = SimpleResponse.class))),
        @APIResponse(responseCode = "401", description = "Authentication required",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response register(WebPushSubscriptionRequest request, @Context ContainerRequestContext ctx) {
        Response authFailure = requireAuthResponse(ctx);
        if (authFailure != null) {
            return authFailure;
        }

        try {
            if (service != null) {
                service.register(authContext(ctx), request);
            }
        } catch (IllegalArgumentException failure) {
            return badRequest("Invalid push subscription");
        } catch (SecurityException failure) {
            return forbidden();
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
    public Response unregister(WebPushSubscriptionRequest request, @Context ContainerRequestContext ctx) {
        Response authFailure = requireAuthResponse(ctx);
        if (authFailure != null) {
            return authFailure;
        }

        try {
            if (service != null) {
                service.unregister(authContext(ctx), request);
            }
        } catch (IllegalArgumentException failure) {
            return badRequest("Invalid push subscription");
        } catch (SecurityException failure) {
            return forbidden();
        }
        return Response.ok(SimpleResponse.ok()).build();
    }

    public Response unregister(@Context ContainerRequestContext ctx) {
        return unregister(null, ctx);
    }

}
