package com.sashplatonov.earnit.kids.resource;

import com.sashplatonov.earnit.kids.config.AuthContext;
import com.sashplatonov.earnit.kids.config.AuthFilter;
import com.sashplatonov.earnit.kids.dto.request.SetPasswordRequest;
import com.sashplatonov.earnit.kids.dto.request.ToggleFamilyBlockRequest;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.service.SuperAdminService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/api/super")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SuperAdminResource {

    private final SuperAdminService superAdminService;

    @GET
    @Path("/families")
    public Response getFamilies(@Context ContainerRequestContext ctx) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("families", superAdminService.getFamilies());
        return Response.ok(payload).build();
    }

    @GET
    @Path("/family/{familyId}/data")
    public Response getFamilyDetails(@Context ContainerRequestContext ctx,
                                     @PathParam("familyId") String familyId) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }

        Map<String, Object> payload = superAdminService.getFamilyDetails(familyId);
        if (payload == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(BackendMessages.message("family.familyNotFound"), "NOT_FOUND", 404))
                .build();
        }
        return Response.ok(payload).build();
    }

    @POST
    @Path("/family/{familyId}/block")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response toggleFamilyBlock(@Context ContainerRequestContext ctx,
                                      @PathParam("familyId") String familyId,
                                      ToggleFamilyBlockRequest request) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }

        if (request == null || request.isBlocked() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(SimpleResponse.error(BackendMessages.message("errors.isBlockedRequired")))
                .build();
        }

        boolean updated = superAdminService.setFamilyBlocked(familyId, request.isBlocked());
        if (!updated) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(SimpleResponse.error(BackendMessages.message("family.familyNotFound")))
                .build();
        }
        return Response.ok(SimpleResponse.ok()).build();
    }

    @POST
    @Path("/family/{familyId}/regenerate-token")
    public Response regenerateFamilyToken(@Context ContainerRequestContext ctx,
                                          @PathParam("familyId") String familyId) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }

        return toTokenResponse(superAdminService.regenerateFamilyToken(familyId));
    }

    @POST
    @Path("/child/{childId}/regenerate-token")
    public Response regenerateChildToken(@Context ContainerRequestContext ctx,
                                         @PathParam("childId") int childId) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }

        return toTokenResponse(superAdminService.regenerateChildToken(childId));
    }

    @POST
    @Path("/family/{familyId}/password")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setFamilyPassword(@Context ContainerRequestContext ctx,
                                      @PathParam("familyId") String familyId,
                                      @Valid SetPasswordRequest request) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }

        return toVoidResponse(
            superAdminService.setFamilyPassword(familyId, request.password()),
            "FAMILY_PASSWORD_UPDATE_FAILED"
        );
    }

    @GET
    @Path("/base-data")
    public Response getBaseData(@Context ContainerRequestContext ctx) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }
        return Response.ok(superAdminService.getBaseData()).build();
    }

    @POST
    @Path("/base-data")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveBaseData(@Context ContainerRequestContext ctx, Map<String, Object> payload) {
        Response authFailure = requireSuperAdmin(ctx);
        if (authFailure != null) {
            return authFailure;
        }

        boolean saved = superAdminService.saveBaseData(payload);
        if (!saved) {
            return Response.serverError()
                .entity(SimpleResponse.error(BackendMessages.message("super.catalogSaveFailed")))
                .build();
        }
        return Response.ok(SimpleResponse.ok()).build();
    }

    private Response toTokenResponse(OperationResult<String> result) {
        if (result instanceof OperationResult.Failure<String> failure) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(SimpleResponse.error(failure.message()))
                .build();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("token", ((OperationResult.Success<String>) result).value());
        return Response.ok(payload).build();
    }

    private Response toVoidResponse(OperationResult<Void> result, String errorCode) {
        if (result instanceof OperationResult.Success<?>) {
            return Response.ok(SimpleResponse.ok()).build();
        }

        OperationResult.Failure<Void> failure = (OperationResult.Failure<Void>) result;
        int status = resolveFailureStatus(failure.errorCode());
        return Response.status(status)
            .entity(ErrorResponse.of(failure.message(), errorCode, status))
            .build();
    }

    private int resolveFailureStatus(String errorCode) {
        return switch (errorCode) {
            case "FAMILY_NOT_FOUND", "CHILD_NOT_FOUND" -> Response.Status.NOT_FOUND.getStatusCode();
            default -> Response.Status.BAD_REQUEST.getStatusCode();
        };
    }

    private Response requireSuperAdmin(ContainerRequestContext ctx) {
        AuthContext auth = auth(ctx);
        if (auth == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ErrorResponse.unauthorized(BackendMessages.message("errors.unauthorized")))
                .build();
        }
        if (!auth.isSuperAdmin()) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(ErrorResponse.of(BackendMessages.message("errors.forbidden"), "FORBIDDEN", 403))
                .build();
        }
        return null;
    }

    private AuthContext auth(ContainerRequestContext ctx) {
        Object property = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return property instanceof AuthContext auth ? auth : null;
    }
}
