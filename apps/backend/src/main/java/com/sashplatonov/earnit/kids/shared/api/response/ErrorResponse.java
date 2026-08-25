package com.sashplatonov.earnit.kids.shared.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String type,
    String title,
    int status,
    String detail,
    String errorCode,
    Map<String, Object> params,
    String traceId
) {
    public ErrorResponse {
        params = params == null || params.isEmpty() ? null : Map.copyOf(params);
    }

    public static ErrorResponse of(String detail) {
        return of(detail, "BAD_REQUEST", 400);
    }

    public static ErrorResponse of(String detail, String errorCode, int status) {
        return of(detail, errorCode, status, Map.of(), null);
    }

    public static ErrorResponse of(String detail, String errorCode, int status, String traceId) {
        return of(detail, errorCode, status, Map.of(), traceId);
    }

    public static ErrorResponse of(
        String detail, String errorCode, int status, Map<String, Object> params, String traceId) {
        return new ErrorResponse(
            resolveType(errorCode), resolveTitle(status), status, detail, errorCode, params, traceId);
    }

    public static ErrorResponse unauthorized(String detail) {
        return of(detail, "UNAUTHORIZED", 401);
    }

    private static String resolveType(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "about:blank";
        }
        return "urn:earnit-kids:problem:" + errorCode.toLowerCase().replace('_', '-');
    }

    private static String resolveTitle(int status) {
        return BackendMessages.statusTitle(status);
    }
}
