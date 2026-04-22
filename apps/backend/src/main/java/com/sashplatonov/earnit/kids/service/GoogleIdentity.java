package com.sashplatonov.earnit.kids.service;

public record GoogleIdentity(
    String email,
    boolean emailVerified
) { }