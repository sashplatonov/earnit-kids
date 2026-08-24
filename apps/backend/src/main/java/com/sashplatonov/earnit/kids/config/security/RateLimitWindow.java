package com.sashplatonov.earnit.kids.config.security;

public record RateLimitWindow(long startedAt, int requests) { }
