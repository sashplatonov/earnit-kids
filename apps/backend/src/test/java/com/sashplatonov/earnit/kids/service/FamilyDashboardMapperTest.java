package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyDashboardMapperTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FamilyDashboardMapper mapper = FamilyDashboardMapper.INSTANCE;

    @Test
    void mapsChildGroupOrdersAndDefaults() {
        ChildEntity child = ChildEntity.builder()
            .id(7)
            .name("Alice")
            .balance(120)
            .monthlyLimit(500)
            .dailyCoinLimit(25)
            .theme("space")
            .taskGroupOrder("[\"Дом\",\"Учеба\",\"Дом\",null,1]")
            .shopGroupOrder("[\"Подарки\",\"Игры\"]")
            .childTaskGroupOrder("[\"Учеба\"]")
            .childShopGroupOrder("broken")
            .build();

        var dto = mapper.toChildDto(child, OBJECT_MAPPER);

        assertThat(dto.id()).isEqualTo(7);
        assertThat(dto.name()).isEqualTo("Alice");
        assertThat(dto.taskGroupOrder()).containsExactly("Дом", "Учеба");
        assertThat(dto.shopGroupOrder()).containsExactly("Подарки", "Игры");
        assertThat(dto.childTaskGroupOrder()).containsExactly("Учеба");
        assertThat(dto.childShopGroupOrder()).isEmpty();
    }

    @Test
    void mapsTaskAndShopDtosWithFrequencyAndTimestamps() throws Exception {
        TaskEntity task = TaskEntity.builder()
            .taskId(11L)
            .childId(7)
            .name("Read")
            .coins(5)
            .groupName("Study")
            .frequency(OBJECT_MAPPER.readTree("{\"limit\":1,\"period\":\"day\"}"))
            .comment("Pages")
            .moneyLimit(300)
            .active(true)
            .build();
        ShopItemEntity shopItem = ShopItemEntity.builder()
            .itemId(22L)
            .childId(7)
            .name("Toy")
            .price(9)
            .groupName("Fun")
            .frequency(OBJECT_MAPPER.readTree("\"weekly\""))
            .comment("Prize")
            .moneyLimit(200)
            .active(false)
            .build();

        var taskDto = mapper.toTaskDto(task, Instant.parse("2026-07-10T10:15:30Z").toString(), OBJECT_MAPPER);
        var shopDto = mapper.toShopItemDto(shopItem, null, OBJECT_MAPPER);

        assertThat(taskDto.id()).isEqualTo(11L);
        assertThat(taskDto.isActive()).isTrue();
        assertThat(taskDto.lastCompletedAt()).isEqualTo("2026-07-10T10:15:30Z");
        assertThat(taskDto.frequency()).isInstanceOf(java.util.Map.class);
        assertThat(((java.util.Map<?, ?>) taskDto.frequency()).get("limit")).isEqualTo(1);

        assertThat(shopDto.id()).isEqualTo(22L);
        assertThat(shopDto.isActive()).isFalse();
        assertThat(shopDto.lastPurchasedAt()).isNull();
        assertThat(shopDto.frequency()).isEqualTo("weekly");
    }
}
