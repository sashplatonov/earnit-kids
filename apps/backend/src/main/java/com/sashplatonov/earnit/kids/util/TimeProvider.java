package com.sashplatonov.earnit.kids.util;

import java.time.Instant;

@FunctionalInterface
public interface TimeProvider {

    Instant now();

    default long currentEpochSecond() {
        return now().getEpochSecond();
    }
}