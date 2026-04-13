package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BaseDataService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Map<String, Object> EMPTY_BASE_DATA = Map.of("tasks", List.of(), "products", List.of());

    private final ObjectMapper objectMapper;
    private Map<String, Object> baseData = EMPTY_BASE_DATA;
    private volatile boolean initialized;

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

    private Map<String, Object> loadBaseData() {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("baseData.json")) {
            if (stream == null) {
                log.warn("baseData.json was not found in the classpath; using an empty catalog");
                return EMPTY_BASE_DATA;
            }
            return objectMapper.readValue(stream, MAP_TYPE);
        } catch (IOException ex) {
            log.error("Failed to read baseData.json; using an empty catalog", ex);
            return EMPTY_BASE_DATA;
        }
    }
}
