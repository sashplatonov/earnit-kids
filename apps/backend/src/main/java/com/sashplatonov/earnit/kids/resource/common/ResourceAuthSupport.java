package com.sashplatonov.earnit.kids.resource.common;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

public abstract class ResourceAuthSupport {

    protected AuthContext authContext(ContainerRequestContext ctx) {
        Object prop = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return prop instanceof AuthContext auth ? auth : null;
    }

    protected AuthContext getAuthOrFail(ContainerRequestContext ctx) {
        return authContext(ctx);
    }

    protected AuthContext requireAuth(ContainerRequestContext ctx) {
        AuthContext auth = authContext(ctx);
        if (auth == null) {
            throw new WebApplicationException(unauthorized());
        }
        return auth;
    }

    protected AuthContext requireAdmin(ContainerRequestContext ctx) {
        AuthContext auth = requireAuth(ctx);
        if (!auth.isAdmin()) {
            throw new WebApplicationException(forbidden());
        }
        return auth;
    }

    protected AuthContext requireChild(ContainerRequestContext ctx) {
        AuthContext auth = requireAuth(ctx);
        if (!auth.isChild()) {
            throw new WebApplicationException(forbidden());
        }
        return auth;
    }

    protected AuthContext requireSuperAdmin(ContainerRequestContext ctx) {
        AuthContext auth = requireAuth(ctx);
        if (!auth.isSuperAdmin()) {
            throw new WebApplicationException(forbidden());
        }
        return auth;
    }

    protected Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
            .entity(ErrorResponse.unauthorized(BackendMessages.message("errors.unauthorized")))
            .build();
    }

    protected Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(ErrorResponse.of(BackendMessages.message("errors.forbidden"), "FORBIDDEN", 403))
            .build();
    }

    protected Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.of(message, "BAD_REQUEST", 400))
            .build();
    }

    protected Response requireAuthResponse(ContainerRequestContext ctx) {
        return authContext(ctx) == null ? unauthorized() : null;
    }

    protected Response requireAdminOrUnauthorized(ContainerRequestContext ctx) {
        AuthContext auth = authContext(ctx);
        return auth == null || !auth.isAdmin() ? unauthorized() : null;
    }

    protected Response requireSuperAdminResponse(ContainerRequestContext ctx) {
        AuthContext auth = authContext(ctx);
        if (auth == null) {
            return unauthorized();
        }
        return auth.isSuperAdmin() ? null : forbidden();
    }

    protected OperationResult<Integer> resolveEffectiveChildId(AuthContext auth, Integer childId) {
        Integer resolved = auth.isChild() ? auth.childId() : childId;
        if (resolved == null) {
            return OperationResult.failure(BackendMessages.message("errors.childIdRequired"));
        }
        return OperationResult.success(resolved);
    }
}
