package com.sashplatonov.earnit.kids.service;

public record GoogleTokenResponse(
    String access_token,
    Integer expires_in,
    String refresh_token,
    String scope,
    String token_type,
    String id_token
) { }
