package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String type,
    String title,
    int status,
    String detail,
    String errorCode
) {
    public static ErrorResponse of(String detail) {
        return of(detail, "BAD_REQUEST", 400);
    }

    public static ErrorResponse of(String detail, String errorCode, int status) {
        return new ErrorResponse(resolveType(errorCode), resolveTitle(status), status, detail, errorCode);
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
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 500 -> "Internal Server Error";
            default -> "Request Failed";
        };
    }
}
