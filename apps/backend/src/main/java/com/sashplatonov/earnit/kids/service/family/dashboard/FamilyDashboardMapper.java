package com.sashplatonov.earnit.kids.service.family.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.ChildDto;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.dto.response.TaskPeriodProgressDto;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FamilyDashboardMapper {
    FamilyDashboardMapper INSTANCE = Mappers.getMapper(FamilyDashboardMapper.class);

    ChildDto toChildDto(ChildEntity child, @Context ObjectMapper objectMapper);

    @Mapping(target = "id", source = "task.taskId")
    @Mapping(target = "isActive", source = "task.active")
    @Mapping(target = "lastCompletedAt", source = "lastCompletedAt")
    @Mapping(target = "periodProgress", source = "periodProgress")
    TaskDto toTaskDto(TaskEntity task, String lastCompletedAt, TaskPeriodProgressDto periodProgress,
                      @Context ObjectMapper objectMapper);

    default TaskDto toTaskDto(TaskEntity task, String lastCompletedAt, ObjectMapper objectMapper) {
        return toTaskDto(task, lastCompletedAt, null, objectMapper);
    }

    @Mapping(target = "id", source = "shopItem.itemId")
    @Mapping(target = "isActive", source = "shopItem.active")
    @Mapping(target = "lastPurchasedAt", source = "lastPurchasedAt")
    ShopItemDto toShopItemDto(ShopItemEntity shopItem, String lastPurchasedAt, @Context ObjectMapper objectMapper);

    default List<String> map(String rawGroupOrder, @Context ObjectMapper objectMapper) {
        if (rawGroupOrder == null || rawGroupOrder.isBlank()) {
            return List.of();
        }

        try {
            JsonNode node = objectMapper.readTree(rawGroupOrder);
            if (!node.isArray()) {
                return List.of();
            }

            List<String> groups = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isTextual()) {
                    continue;
                }

                String value = item.asText().trim();
                if (!value.isEmpty() && !groups.contains(value)) {
                    groups.add(value);
                }
            }

            return List.copyOf(groups);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    default Object map(JsonNode rawFrequency, @Context ObjectMapper objectMapper) {
        if (rawFrequency == null || rawFrequency.isNull()) {
            return null;
        }

        if (rawFrequency.isTextual()) {
            String value = rawFrequency.asText();
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readValue(value, Object.class);
            } catch (Exception ex) {
                return value;
            }
        }

        return objectMapper.convertValue(rawFrequency, Object.class);
    }

    default String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
