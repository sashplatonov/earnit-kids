package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class BaseDataService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Map<String, Object> EMPTY_BASE_DATA = Map.of("tasks", List.of(), "products", List.of());

    private final ObjectMapper objectMapper;
    private final Path baseDataFilePath;
    private Map<String, Object> baseData = EMPTY_BASE_DATA;
    private volatile boolean initialized;

    @Inject
    public BaseDataService(ObjectMapper objectMapper) {
        this(objectMapper, resolveBaseDataFilePath());
    }

    BaseDataService(ObjectMapper objectMapper, Path baseDataFilePath) {
        this.objectMapper = objectMapper;
        this.baseDataFilePath = baseDataFilePath;
    }

    @PostConstruct
    void initialize() {
        baseData = loadBaseData();
        initialized = true;
    }

    public Map<String, Object> getBaseData() {
        if (!initialized) {
            initialize();
        }
        return baseData;
    }

    public synchronized boolean saveBaseData(Map<String, Object> updatedBaseData) {
        Map<String, Object> normalized = normalizeBaseData(updatedBaseData);
        try {
            Path parent = baseDataFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(baseDataFilePath.toFile(), normalized);
            baseData = normalized;
            initialized = true;
            return true;
        } catch (IOException ex) {
            log.error("Failed to persist base data to {}", baseDataFilePath, ex);
            return false;
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

        Map<String, Object> normalized = objectMapper.convertValue(rawBaseData, MAP_TYPE);
        normalized.putIfAbsent("tasks", List.of());
        normalized.putIfAbsent("products", List.of());
        return normalized;
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
