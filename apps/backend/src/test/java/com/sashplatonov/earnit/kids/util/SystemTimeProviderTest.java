package com.sashplatonov.earnit.kids.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SystemTimeProviderTest {

    @Test
    void now_returnsCurrentInstant() {
        SystemTimeProvider provider = new SystemTimeProvider();
        Instant before = Instant.now();

        Instant result = provider.now();

        Instant after = Instant.now();
        assertThat(result).isBetween(before, after);
    }
}