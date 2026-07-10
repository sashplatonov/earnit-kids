package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatabaseHealthResponse(
    DbHealth db
) {
    public record DbHealth(
        boolean connected,
        Long pingMs,
        String lastError
    ) { }
}
