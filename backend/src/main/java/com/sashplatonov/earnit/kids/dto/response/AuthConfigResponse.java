package com.sashplatonov.earnit.kids.dto.response;

/**
 * Authentication configuration flags.
 */
public record AuthConfigResponse(
    boolean emailVerificationEnabled,
    boolean passwordRecoveryEnabled
) { }
