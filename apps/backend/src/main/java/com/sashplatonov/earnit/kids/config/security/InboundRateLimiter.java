package com.sashplatonov.earnit.kids.config.security;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InboundRateLimiter {
    private final Map<String, RateLimitWindow> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maximumEntries;

    public InboundRateLimiter() {
        this(Clock.systemUTC(), 10_000);
    }

    InboundRateLimiter(Clock clock) {
        this(clock, 10_000);
    }

    InboundRateLimiter(Clock clock, int maximumEntries) {
        this.clock = clock;
        this.maximumEntries = maximumEntries;
    }

    public RateLimitDecision tryAcquire(String route, String client, int limit, long windowSeconds) {
        long now = clock.millis();
        long windowMillis = windowSeconds * 1_000L;
        if (windows.size() >= maximumEntries) {
            windows.keySet().stream().findFirst().ifPresent(windows::remove);
        }
        RateLimitWindow window = windows.compute(route + "\n" + client, (key, current) -> {
            if (current == null || now - current.startedAt() >= windowMillis) {
                return new RateLimitWindow(now, 1);
            }
            return new RateLimitWindow(current.startedAt(), current.requests() + 1);
        });
        if (window.requests() <= limit) {
            return RateLimitDecision.permit();
        }
        long remainingMillis = windowMillis - (now - window.startedAt());
        return RateLimitDecision.reject(Math.max(1, (remainingMillis + 999) / 1_000));
    }

    void clear() {
        windows.clear();
    }

}
