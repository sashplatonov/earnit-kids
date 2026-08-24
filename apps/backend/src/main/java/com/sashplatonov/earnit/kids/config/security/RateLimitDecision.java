package com.sashplatonov.earnit.kids.config.security;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    static RateLimitDecision permit() {
        return new RateLimitDecision(true, 0);
    }

    static RateLimitDecision reject(long retryAfterSeconds) {
        return new RateLimitDecision(false, retryAfterSeconds);
    }
}
