package com.sashplatonov.earnit.kids.family.application.catalog;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemEntity;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemType;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.CatalogItemRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalizedCatalogServiceImplTest {
    private final CatalogItemRepository repository = mock(CatalogItemRepository.class);
    private final LocalizedCatalogServiceImpl service = new LocalizedCatalogServiceImpl(repository);

    @Test
    void mapsBothKindsWithRequestedLocaleAndDeterministicTags() {
        CatalogItemEntity task = item(CatalogItemType.task, "ct-2", "Read together", "Читать вместе", 3, null);
        task.setSortOrder(2);
        CatalogItemEntity reward = item(CatalogItemType.reward, "cr-1", "Choose a game", "Выбрать игру", null, 8);
        reward.setSortOrder(1);
        when(repository.findActiveOrdered()).thenReturn(List.of(task, reward));

        Map<String, Object> english = service.getBaseData(FamilyLocale.en);
        Map<String, Object> russian = service.getBaseData(FamilyLocale.ru);
        Map<String, Object> enTask = first(english, "tasks");
        Map<String, Object> ruTask = first(russian, "tasks");
        Map<String, Object> enReward = first(english, "rewards");

        assertThat(enTask).containsEntry("id", "ct-2").containsEntry("title", "Read together")
            .containsEntry("coins", 3).containsEntry("tags", List.of("study"));
        assertThat(ruTask).containsEntry("title", "Читать вместе").containsEntry("groupName", "Учёба");
        assertThat(enReward).containsEntry("id", "cr-1").containsEntry("price", 8)
            .doesNotContainKey("coins");
        assertThat(english).containsKey("tasks").containsKey("catalog");
        assertThat(russian).containsEntry("tasks", List.of());
    }

    @Test
    void defaultsNullLocaleToEnglishAndPreservesNullComment() {
        CatalogItemEntity task = item(CatalogItemType.task, "ct-1", "English", "Русский", 1, null);
        task.setCommentEn(null);
        when(repository.findActiveOrdered()).thenReturn(List.of(task));

        Map<String, Object> mapped = first(service.getBaseData(null), "tasks");

        assertThat(mapped.get("title")).isEqualTo("English");
        assertThat(mapped.get("comment")).isNull();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> first(Map<String, Object> data, String kind) {
        return (Map<String, Object>) ((List<?>) ((Map<?, ?>) data.get("catalog")).get(kind)).get(0);
    }

    private static CatalogItemEntity item(CatalogItemType type, String id, String en, String ru,
                                          Integer coins, Integer price) {
        return CatalogItemEntity.builder()
            .externalId(id)
            .itemType(type)
            .titleEn(en)
            .titleRu(ru)
            .groupKey("study")
            .groupNameEn("Study")
            .groupNameRu("Учёба")
            .semanticGraphicKey("book")
            .frequencyLimit(1)
            .frequencyPeriod("day")
            .minAge(6)
            .maxAge(8)
            .difficulty("simple")
            .active(true)
            .sortOrder(1)
            .coins(coins)
            .price(price)
            .build();
    }
}
