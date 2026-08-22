package com.sashplatonov.earnit.kids.dto.response;

public record AuthConfigResponse(
    boolean googleEnabled,
    String googleClientId
) { }
