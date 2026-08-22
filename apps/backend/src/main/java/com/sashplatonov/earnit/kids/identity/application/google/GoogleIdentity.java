package com.sashplatonov.earnit.kids.identity.application.google;

public record GoogleIdentity(
    String email,
    boolean emailVerified
) { }
