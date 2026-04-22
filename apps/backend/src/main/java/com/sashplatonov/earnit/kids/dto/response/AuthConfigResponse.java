package com.sashplatonov.earnit.kids.dto.response;

public record AuthConfigResponse(
    boolean emailVerificationEnabled,
    boolean passwordRecoveryEnabled,
    boolean googleEnabled,
    String googleClientId
) { }
