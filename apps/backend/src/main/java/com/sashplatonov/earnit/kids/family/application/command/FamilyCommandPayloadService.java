package com.sashplatonov.earnit.kids.family.application.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@ApplicationScoped
final class FamilyCommandPayloadService {
    private final ObjectMapper objectMapper;

    @Inject
    FamilyCommandPayloadService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<Map<String, Object>> asMapList(Object rawValue) {
        if (!(rawValue instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                result.add(objectMapper.convertValue(
                    map,
                    new TypeReference<Map<String, Object>>() { }
                ));
            }
        }
        return result;
    }

    String asNullableString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s.isBlank() ? null : s;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    JsonNode serializeFrequency(Object rawFrequency) {
        if (rawFrequency == null) {
            return null;
        }
        if (rawFrequency instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        if (rawFrequency instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readTree(text);
            } catch (Exception ex) {
                return new ObjectMapper().valueToTree(text);
            }
        }

        try {
            return objectMapper.valueToTree(rawFrequency);
        } catch (Exception ex) {
            return null;
        }
    }

    Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    Integer coalesceInt(Object primary, Object fallback) {
        Integer primaryValue = asInteger(primary);
        return primaryValue != null ? primaryValue : asInteger(fallback);
    }

    Object coalesceFirst(Object primary, Object fallback) {
        return primary != null ? primary : fallback;
    }

    int defaultInt(Object value, int defaultValue) {
        Integer parsed = asInteger(value);
        return parsed != null ? parsed : defaultValue;
    }

    boolean defaultBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
