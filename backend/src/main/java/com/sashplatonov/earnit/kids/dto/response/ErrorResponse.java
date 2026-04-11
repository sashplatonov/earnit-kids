package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Error response following RFC 7807 Problem Details pattern.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String error,
    String errorCode,
    Integer status
) {
    /** Creates an error response with message only. */
    public static ErrorResponse of(String error) {
        return new ErrorResponse(error, null, null);
    }

    /** Creates a full error response. */
    public static ErrorResponse of(String error, String errorCode, int status) {
        return new ErrorResponse(error, errorCode, status);
    }
}
