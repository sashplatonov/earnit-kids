package com.sashplatonov.earnit.kids.family.infrastructure.persistence.family;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.cache.CaffeineCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class FamilyDbIdCache {
    public static final String CACHE_NAME = "family-db-id";

    private final CaffeineCache cache;

    @Inject
    public FamilyDbIdCache(@CacheName(CACHE_NAME) Cache cache) {
        this.cache = cache.as(CaffeineCache.class);
    }

    public Optional<Integer> get(String familyId) {
        CompletableFuture<Integer> cached = cache.getIfPresent(familyId);
        return cached == null ? Optional.empty() : Optional.ofNullable(cached.join());
    }

    public void put(String familyId, int familyDbId) {
        cache.put(familyId, CompletableFuture.completedFuture(familyDbId));
    }
}
