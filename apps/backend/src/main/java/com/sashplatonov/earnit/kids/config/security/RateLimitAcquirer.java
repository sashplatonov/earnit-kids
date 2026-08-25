package com.sashplatonov.earnit.kids.config.security;

@FunctionalInterface
interface RateLimitAcquirer {
    RateLimitDecision tryAcquire(String route, String client, int limit, long windowSeconds);
}
