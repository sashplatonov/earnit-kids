package com.sashplatonov.earnit.kids.service.analytics;

import java.time.Duration;
import java.util.Locale;

enum AnalyticsTimeframe {
    WEEK(Duration.ofDays(7)),
    MONTH(Duration.ofDays(30)),
    YEAR(Duration.ofDays(365));

    private final Duration duration;

    AnalyticsTimeframe(Duration duration) {
        this.duration = duration;
    }

    static AnalyticsTimeframe from(String value) {
        if (value == null || value.isBlank()) {
            return MONTH;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MONTH;
        }
    }

    Duration duration() {
        return duration;
    }

    String cacheKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
