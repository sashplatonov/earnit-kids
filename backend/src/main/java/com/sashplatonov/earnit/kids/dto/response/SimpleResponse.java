package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic success/error response used across endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimpleResponse(
    boolean success,
    String error
) {
    /** Creates a successful response. */
    public static SimpleResponse ok() {
        return new SimpleResponse(true, null);
    }

    /** Creates a failure response. */
    public static SimpleResponse error(String error) {
        return new SimpleResponse(false, error);
    }
}
