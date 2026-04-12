package com.sashplatonov.earnit.kids.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "compat.jwt")
public interface JwtCompatibilityConfig {

    String secret();
}