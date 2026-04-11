package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class BaseDataService {
    private static final Logger LOG = Logger.getLogger(BaseDataService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Map<String, Object> EMPTY_BASE_DATA = Map.of("tasks", List.of(), "products", List.of());

    private final ObjectMapper objectMapper;
    private final Map<String, Object> baseData;

    @Inject
    public BaseDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.baseData = loadBaseData();
    }

    public Map<String, Object> getBaseData() {
        return baseData;
    }

    private Map<String, Object> loadBaseData() {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("baseData.json")) {
            if (stream == null) {
                LOG.warn("baseData.json was not found in classpath; using empty catalog");
                return EMPTY_BASE_DATA;
            }
            return objectMapper.readValue(stream, MAP_TYPE);
        } catch (Exception ex) {
            LOG.error("Failed to read baseData.json; using empty catalog", ex);
            return EMPTY_BASE_DATA;
        }
    }
}
