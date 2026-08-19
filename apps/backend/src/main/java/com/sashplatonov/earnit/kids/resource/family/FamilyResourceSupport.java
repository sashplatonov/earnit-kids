package com.sashplatonov.earnit.kids.resource.family;

import com.sashplatonov.earnit.kids.config.auth.AuthContext;
import com.sashplatonov.earnit.kids.dto.response.ChildInfo;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.resource.common.ResourceAuthSupport;
import com.sashplatonov.earnit.kids.service.family.FamilyParentAccessService;
import com.sashplatonov.earnit.kids.service.family.FamilyService;
import com.sashplatonov.earnit.kids.service.websocket.WebSocketNotificationService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.OperationResultResponses;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

abstract class FamilyResourceSupport extends ResourceAuthSupport {

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

    protected <T> Response toResponse(OperationResult<T> result) {
        return OperationResultResponses.toOk(result);
    }

    protected Response toVoidResponse(OperationResult<Void> result) {
        return OperationResultResponses.toVoidOk(result);
    }

    protected String errorCodeOrBadRequest(String errorCode) {
        return OperationResultResponses.errorCodeOrBadRequest(errorCode);
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
