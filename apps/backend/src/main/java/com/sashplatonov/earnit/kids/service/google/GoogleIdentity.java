package com.sashplatonov.earnit.kids.service.google;

public record GoogleIdentity(
    String email,
    boolean emailVerified
) { }
