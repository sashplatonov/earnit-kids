package com.sashplatonov.earnit.kids.repository.cache;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CaffeineCache;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FamilyDbIdCacheTest {

    @Test
    void get_returnsOnlyValuesAlreadyPresentInCache() {
        Cache quarkusCache = mock(Cache.class);
        CaffeineCache caffeineCache = mock(CaffeineCache.class);
        when(quarkusCache.as(CaffeineCache.class)).thenReturn(caffeineCache);
        when(caffeineCache.getIfPresent("known")).thenReturn(CompletableFuture.completedFuture(7));

        FamilyDbIdCache cache = new FamilyDbIdCache(quarkusCache);

        assertThat(cache.get("missing")).isEmpty();
        assertThat(cache.get("known")).contains(7);
    }

    @Test
    void put_storesCompletedPositiveValue() {
        Cache quarkusCache = mock(Cache.class);
        CaffeineCache caffeineCache = mock(CaffeineCache.class);
        when(quarkusCache.as(CaffeineCache.class)).thenReturn(caffeineCache);
        FamilyDbIdCache cache = new FamilyDbIdCache(quarkusCache);

        cache.put("family", 11);

        verify(caffeineCache).put(
            eq("family"),
            argThat(cached -> Integer.valueOf(11).equals(cached.join()))
        );
    }
}
