package com.sashplatonov.earnit.kids.service.family;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryDtoMapperTest {

    private final HistoryDtoMapper mapper = new HistoryDtoMapper(FamilyDashboardMapper.INSTANCE);

    private static HistoryEntryEntity entry(HistoryEntryType type, Long relatedId, String description) {
        return HistoryEntryEntity.builder()
            .id(1L)
            .childId(5)
            .type(type)
            .amount(10)
            .description(description)
            .relatedId(relatedId)
            .createdAt(Instant.parse("2026-08-01T10:00:00Z"))
            .build();
    }

    @Test
    void toDto_earnEntryWithRelatedTask_resolvesTaskDetails() {
        HistoryEntryEntity entry = entry(HistoryEntryType.earn, 100L, "did chores");
        TaskDto task = new TaskDto(100L, "Chores", 10, "Home", null, null, null, null, null,
            true, 5, null, null);
        Map<Long, TaskDto> taskMap = Map.of(100L, task);

        HistoryEntryDto dto = mapper.toDto(entry, taskMap, Map.of());

        assertThat(dto.taskId()).isEqualTo(100L);
        assertThat(dto.taskName()).isEqualTo("Chores");
        assertThat(dto.title()).isEqualTo("did chores");
        assertThat(dto.createdAt()).isEqualTo("2026-08-01T10:00:00Z");
    }

    @Test
    void toDto_spendEntryWithRelatedShopItem_resolvesShopDetails() {
        HistoryEntryEntity entry = entry(HistoryEntryType.spend, 200L, "bought toy");
        ShopItemDto item = new ShopItemDto(200L, "Toy", 50, "Toys", null, null, null, true, 5, null);
        Map<Long, ShopItemDto> shopMap = Map.of(200L, item);

        HistoryEntryDto dto = mapper.toDto(entry, Map.of(), shopMap);

        assertThat(dto.itemId()).isEqualTo(200L);
        assertThat(dto.itemName()).isEqualTo("Toy");
        assertThat(dto.title()).isEqualTo("bought toy");
    }

    @Test
    void toDto_entryWithNullRelatedId_hasEmptyDetails() {
        HistoryEntryEntity entry = entry(HistoryEntryType.earn, null, "manual entry");

        HistoryEntryDto dto = mapper.toDto(entry, Map.of(), Map.of());

        assertThat(dto.taskId()).isNull();
        assertThat(dto.itemId()).isNull();
        assertThat(dto.title()).isEqualTo("manual entry");
    }
}
