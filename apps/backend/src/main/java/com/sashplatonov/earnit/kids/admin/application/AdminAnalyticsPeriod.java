package com.sashplatonov.earnit.kids.admin.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

// EXPLAIN: Normalized period contract shared by the admin dashboard analytics.
public record AdminAnalyticsPeriod(String value, Instant start) {

    public static final String DEFAULT_VALUE = "30d";

    public static AdminAnalyticsPeriod parse(String value) {
        return parse(value, Instant.now());
    }

    public static AdminAnalyticsPeriod parse(String value, Instant now) {
        String normalized = value == null || value.isBlank() ? DEFAULT_VALUE : value;
        return switch (normalized) {
            case "7d" -> new AdminAnalyticsPeriod(normalized, now.minus(7, ChronoUnit.DAYS));
            case "30d" -> new AdminAnalyticsPeriod(normalized, now.minus(30, ChronoUnit.DAYS));
            case "90d" -> new AdminAnalyticsPeriod(normalized, now.minus(90, ChronoUnit.DAYS));
            case "all" -> new AdminAnalyticsPeriod(normalized, Instant.EPOCH);
            default -> throw new IllegalArgumentException("Unsupported analytics period: " + normalized);
        };
    }
}
