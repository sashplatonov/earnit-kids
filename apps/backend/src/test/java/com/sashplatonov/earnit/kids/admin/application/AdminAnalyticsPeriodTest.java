package com.sashplatonov.earnit.kids.admin.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAnalyticsPeriodTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Test
    void parsesSupportedValuesAndDefault() {
        assertThat(AdminAnalyticsPeriod.parse("7d", NOW).start()).isEqualTo(NOW.minusSeconds(7 * 24 * 60 * 60));
        assertThat(AdminAnalyticsPeriod.parse("30d", NOW).start()).isEqualTo(NOW.minusSeconds(30 * 24 * 60 * 60));
        assertThat(AdminAnalyticsPeriod.parse("90d", NOW).start()).isEqualTo(NOW.minusSeconds(90 * 24 * 60 * 60));
        assertThat(AdminAnalyticsPeriod.parse("all", NOW).start()).isEqualTo(Instant.EPOCH);
        assertThat(AdminAnalyticsPeriod.parse(null, NOW).value()).isEqualTo("30d");
    }

    @Test
    void rejectsUnsupportedValuesInsteadOfUsingLifetime() {
        assertThatThrownBy(() -> AdminAnalyticsPeriod.parse("lifetime", NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(AdminAnalyticsPeriod.parse("all", NOW).start()).isEqualTo(Instant.EPOCH);
    }
}
