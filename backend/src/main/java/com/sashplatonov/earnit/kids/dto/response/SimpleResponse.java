package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimpleResponse(
    boolean success,
    String error
) {
    public static SimpleResponse ok() {
        return new SimpleResponse(true, null);
    }

    public static SimpleResponse error(String error) {
        return new SimpleResponse(false, error);
    }
}
