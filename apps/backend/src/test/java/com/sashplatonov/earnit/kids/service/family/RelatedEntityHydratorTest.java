package com.sashplatonov.earnit.kids.service.family;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.service.family.dashboard.FamilyDashboardMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelatedEntityHydratorTest {

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final ShopItemRepository shopItemRepository = mock(ShopItemRepository.class);
    private final RelatedEntityHydrator hydrator = new RelatedEntityHydrator(
        taskRepository, shopItemRepository, FamilyDashboardMapper.INSTANCE, new ObjectMapper());

    private static HistoryEntryEntity earnEntry(Long relatedId) {
        return HistoryEntryEntity.builder()
            .id(1L).childId(5).type(HistoryEntryType.earn).amount(10).relatedId(relatedId).build();
    }

    @Test
    void hydrateMissingHistoryEntries_entriesAlreadyInMap_doesNotFetch() {
        HistoryEntryEntity entry = earnEntry(100L);
        Map<Long, TaskDto> taskMap = new LinkedHashMap<>();
        taskMap.put(100L, new TaskDto(100L, "Chores", 10, "Home", null, null, null, null, null,
            true, 5, null, null));

        hydrator.hydrateMissingHistoryEntries(42, 5, List.of(entry), taskMap, new LinkedHashMap<>());

        verify(taskRepository, never()).findByFamilyAndChildAndTaskIds(eq(42), anyList(), anyList());
    }

    @Test
    void hydrateMissingHistoryEntries_missingTask_fetchesAndPopulates() {
        HistoryEntryEntity entry = earnEntry(100L);
        TaskEntity task = TaskEntity.builder().taskId(100L).name("Chores").coins(10).build();
        when(taskRepository.findByFamilyAndChildAndTaskIds(42, List.of(5), List.of(100L)))
            .thenReturn(List.of(task));

        Map<Long, TaskDto> taskMap = new LinkedHashMap<>();
        hydrator.hydrateMissingHistoryEntries(42, 5, List.of(entry), taskMap, new LinkedHashMap<>());

        assertThat(taskMap).containsKey(100L);
        assertThat(taskMap.get(100L).name()).isEqualTo("Chores");
    }
}
