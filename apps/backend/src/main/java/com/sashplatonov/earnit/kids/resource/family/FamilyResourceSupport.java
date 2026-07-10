package com.sashplatonov.earnit.kids.resource.family;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.config.auth.AuthFilter;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.ErrorResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.SimpleResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.service.family.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.service.websocket.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

abstract class FamilyResourceSupport {

    protected final FamilyService familyService;
    protected final WebSocketNotificationService webSocketNotificationService;
    protected final FamilyParentAccessService familyParentAccessService;

    FamilyResourceSupport(FamilyService familyService,
                          WebSocketNotificationService webSocketNotificationService,
                          FamilyParentAccessService familyParentAccessService) {
        this.familyService = familyService;
        this.webSocketNotificationService = webSocketNotificationService;
        this.familyParentAccessService = familyParentAccessService;
    }

    protected AuthContext getAuthOrFail(ContainerRequestContext ctx) {
        Object prop = ctx.getProperty(AuthFilter.AUTH_CONTEXT_PROPERTY);
        return prop instanceof AuthContext auth ? auth : null;
    }

    protected Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
            .entity(ErrorResponse.unauthorized(BackendMessages.message("errors.unauthorized")))
            .build();
    }

    protected Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.of(message, "BAD_REQUEST", 400))
            .build();
    }

    protected <T> Response toResponse(OperationResult<T> result) {
        return switch (result) {
            case OperationResult.Success<T> s -> Response.ok(s.value()).build();
            case OperationResult.Failure<T> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(f.message(), errorCodeOrBadRequest(f.errorCode()), 400)).build();
        };
    }

    protected Response toVoidResponse(OperationResult<Void> result) {
        return switch (result) {
            case OperationResult.Success<Void> ignored -> Response.ok(SimpleResponse.ok()).build();
            case OperationResult.Failure<Void> f ->
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.of(f.message(), errorCodeOrBadRequest(f.errorCode()), 400)).build();
        };
    }

    protected String errorCodeOrBadRequest(String errorCode) {
        return errorCode != null ? errorCode : "BAD_REQUEST";
    }

    protected void notifyDataUpdated(AuthContext auth, Integer childId, OperationResult<FamilyDataResponse> result) {
        if (!(result instanceof OperationResult.Success<FamilyDataResponse>)) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("by", auth.role());
        if (childId != null) {
            payload.put("childId", childId);
        }
        webSocketNotificationService.notifyFamily(auth.familyId(), "DATA_UPDATED", payload);
    }

    protected void notifyChildDeleted(String familyId, int childId, OperationResult<Void> result) {
        if (!(result instanceof OperationResult.Success<Void>)) {
            return;
        }
        webSocketNotificationService.notifyFamily(familyId, "CHILD_DELETED", Map.of("childId", childId));
    }

    protected void notifyChildUpdated(String familyId, int childId, OperationResult<Void> result) {
        if (!(result instanceof OperationResult.Success<Void>)) {
            return;
        }
        webSocketNotificationService.notifyFamily(familyId, "CHILD_UPDATED", Map.of("childId", childId));
    }

    protected void notifyChildUpdated(String familyId, OperationResult<ChildInfo> result,
                                      Function<ChildInfo, Integer> childIdExtractor) {
        if (!(result instanceof OperationResult.Success<ChildInfo> success)) {
            return;
        }
        Integer childId = childIdExtractor.apply(success.value());
        if (childId == null) {
            return;
        }
        webSocketNotificationService.notifyFamily(familyId, "CHILD_UPDATED", Map.of("childId", childId));
    }
}
