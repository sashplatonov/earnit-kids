package com.sashplatonov.earnit.kids.service.database;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class BaseDataService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Map<String, Object> EMPTY_BASE_DATA = Map.of("tasks", List.of());
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper;
    private final Path baseDataFilePath;
    private final TimeProvider timeProvider;
    private final Duration cacheTtl;
    private Map<String, Object> baseData = EMPTY_BASE_DATA;
    private volatile boolean initialized;
    private volatile Instant cacheLoadedAt;
    private final Object baseDataLock = new Object();

    @Inject
    public BaseDataService(ObjectMapper objectMapper,
                           TimeProvider timeProvider,
                           @ConfigProperty(name = "app.performance.cache.base-data-ttl") Duration cacheTtl) {
        this(objectMapper, resolveBaseDataFilePath(), timeProvider, cacheTtl);
    }

    BaseDataService(ObjectMapper objectMapper, Path baseDataFilePath) {
        this(objectMapper, baseDataFilePath, Instant::now, DEFAULT_CACHE_TTL);
    }

    BaseDataService(ObjectMapper objectMapper, Path baseDataFilePath, TimeProvider timeProvider, Duration cacheTtl) {
        this.objectMapper = objectMapper;
        this.baseDataFilePath = baseDataFilePath;
        this.timeProvider = timeProvider;
        this.cacheTtl = cacheTtl == null ? DEFAULT_CACHE_TTL : cacheTtl;
    }

    @PostConstruct
    void initialize() {
        Map<String, Object> loaded = loadBaseData();
        synchronized (baseDataLock) {
            baseData = loaded;
            initialized = true;
            cacheLoadedAt = timeProvider.now();
        }
    }

    @CacheResult(cacheName = "base-data")
    public Map<String, Object> getBaseData() {
        if (!initialized) {
            initialize();
        }
        synchronized (baseDataLock) {
            if (isCacheExpired()) {
                initialize();
            }
            return Map.copyOf(baseData);
        }
    }

    @CacheInvalidateAll(cacheName = "base-data")
    public boolean saveBaseData(Map<String, Object> updatedBaseData) {
        Map<String, Object> normalized = normalizeBaseData(updatedBaseData);
        synchronized (baseDataLock) {
            try {
                Path parent = baseDataFilePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(baseDataFilePath.toFile(), normalized);
                invalidateCache();
                baseData = normalized;
                initialized = true;
                cacheLoadedAt = timeProvider.now();
                return true;
            } catch (IOException ex) {
                log.error("Failed to persist base data to {}", baseDataFilePath, ex);
                return false;
            }
        }
    }

    void invalidateCache() {
        synchronized (baseDataLock) {
            initialized = false;
            cacheLoadedAt = null;
        }
    }

    private Map<String, Object> loadBaseData() {
        Map<String, Object> fromFile = loadPersistedBaseData();
        if (fromFile != null) {
            return fromFile;
        }

        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("baseData.json")) {
            if (stream == null) {
                log.warn("baseData.json was not found in the classpath; using an empty catalog");
                return EMPTY_BASE_DATA;
            }
            return normalizeBaseData(objectMapper.readValue(stream, MAP_TYPE));
        } catch (IOException ex) {
            log.error("Failed to read baseData.json; using an empty catalog", ex);
            return EMPTY_BASE_DATA;
        }
    }

    private Map<String, Object> loadPersistedBaseData() {
        if (!Files.exists(baseDataFilePath)) {
            return null;
        }

        try {
            return normalizeBaseData(objectMapper.readValue(baseDataFilePath.toFile(), MAP_TYPE));
        } catch (IOException ex) {
            log.error("Failed to read persisted base data from {}", baseDataFilePath, ex);
            return null;
        }
    }

    private Map<String, Object> normalizeBaseData(Map<String, Object> rawBaseData) {
        if (rawBaseData == null || rawBaseData.isEmpty()) {
            return EMPTY_BASE_DATA;
        }

        Map<String, Object> normalized = new HashMap<>(objectMapper.convertValue(rawBaseData, MAP_TYPE));
        normalized.putIfAbsent("tasks", List.of());
        normalized.replaceAll((key, value) -> value instanceof List<?> list ? List.copyOf(list) : value);
        return Map.copyOf(normalized);
    }

    private boolean isCacheExpired() {
        if (!initialized || cacheLoadedAt == null) {
            return true;
        }
        if (cacheTtl.isZero() || cacheTtl.isNegative()) {
            return true;
        }
        return Duration.between(cacheLoadedAt, timeProvider.now()).compareTo(cacheTtl) >= 0;
    }

    private static Path resolveBaseDataFilePath() {
        String configuredDataDir = firstNonBlank(
            System.getProperty("earnit.data-dir"),
            System.getenv("DATA_DIR")
        );
        Path dataDir = configuredDataDir == null ? Path.of("data") : Path.of(configuredDataDir);
        return dataDir.resolve("baseData.json");
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }
}
