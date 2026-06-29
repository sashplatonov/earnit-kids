package com.sashplatonov.earnit.kids.util;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public final class SystemTimeProvider implements TimeProvider {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
