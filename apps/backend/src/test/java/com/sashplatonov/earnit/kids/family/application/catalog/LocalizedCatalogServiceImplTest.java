package com.sashplatonov.earnit.kids.family.application.catalog;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemEntity;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemType;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemTranslationEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.CatalogItemRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.CatalogItemTranslationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalizedCatalogServiceImplTest {
    private final CatalogItemRepository repository = mock(CatalogItemRepository.class);
    private final CatalogItemTranslationRepository translationRepository = mock(CatalogItemTranslationRepository.class);
    private final LocalizedCatalogServiceImpl service = new LocalizedCatalogServiceImpl(repository, translationRepository);

    @Test
    void mapsBothKindsWithRequestedLocaleAndDeterministicTags() {
        CatalogItemEntity task = item(CatalogItemType.task, "ct-2", "Read together", "Читать вместе", 3, null);
        task.setId(1L);
        task.setSortOrder(2);
        CatalogItemEntity reward = item(CatalogItemType.reward, "cr-1", "Choose a game", "Выбрать игру", null, 8);
        reward.setId(2L);
        reward.setSortOrder(1);
        when(repository.findActiveOrdered()).thenReturn(List.of(task, reward));
        when(translationRepository.findForItemsAndLocales(anyList(), anyList()))
            .thenReturn(List.of(translation(task, "en", "Read together", "Study"),
                translation(task, "ru", "Читать вместе", "Учёба"),
                translation(reward, "en", "Choose a game", "Rewards")));

        Map<String, Object> english = service.getBaseData(FamilyLocale.en);
        Map<String, Object> russian = service.getBaseData(FamilyLocale.ru);
        Map<?, ?> enTask = first(english, "tasks");
        Map<?, ?> ruTask = first(russian, "tasks");
        Map<?, ?> enReward = first(english, "rewards");

        assertThat(enTask.get("id")).isEqualTo("ct-2");
        assertThat(enTask.get("title")).isEqualTo("Read together");
        assertThat(enTask.get("coins")).isEqualTo(3);
        assertThat(enTask.get("tags")).isEqualTo(List.of("study"));
        assertThat(ruTask.get("title")).isEqualTo("Читать вместе");
        assertThat(ruTask.get("groupName")).isEqualTo("Учёба");
        assertThat(enReward.get("id")).isEqualTo("cr-1");
        assertThat(enReward.get("price")).isEqualTo(8);
        assertThat(enReward.containsKey("coins")).isFalse();
        assertThat(english).containsKey("tasks").containsKey("catalog");
        assertThat(russian).containsEntry("tasks", List.of());
    }

    @Test
    void defaultsNullLocaleToEnglishAndPreservesNullComment() {
        CatalogItemEntity task = item(CatalogItemType.task, "ct-1", "English", "Русский", 1, null);
        task.setId(1L);
        when(repository.findActiveOrdered()).thenReturn(List.of(task));
        when(translationRepository.findForItemsAndLocales(anyList(), anyList()))
            .thenReturn(List.of(translation(task, "en", "English", "Study")));

        Map<?, ?> mapped = first(service.getBaseData(null), "tasks");

        assertThat(mapped.get("title")).isEqualTo("English");
        assertThat(mapped.get("comment")).isNull();
    }

    @Test
    void usesEnglishFallbackAndSupportsAnyLocaleCode() {
        CatalogItemEntity task = item(CatalogItemType.task, "ct-3", "ignored", "Игнор", 1, null);
        Map<String, CatalogItemTranslationEntity> rows = Map.of(
            "en", translation(task, "en", "English", "Study"));

        assertThat(service.resolveTranslation(task, rows, "de").getTitle()).isEqualTo("English");
    }

    @Test
    void rejectsItemsWithoutEnglishFallback() {
        CatalogItemEntity task = item(CatalogItemType.task, "ct-4", "ignored", "Игнор", 1, null);

        assertThatThrownBy(() -> service.resolveTranslation(task, Map.of(), "de"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ct-4");
    }

    private static Map<?, ?> first(Map<String, Object> data, String kind) {
        return (Map<?, ?>) ((List<?>) ((Map<?, ?>) data.get("catalog")).get(kind)).get(0);
    }

    private static CatalogItemEntity item(CatalogItemType type, String id, String en, String ru,
                                          Integer coins, Integer price) {
        return CatalogItemEntity.builder()
            .externalId(id)
            .itemType(type)
            .groupKey("study")
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

    private static CatalogItemTranslationEntity translation(CatalogItemEntity item, String locale,
                                                             String title, String groupName) {
        return CatalogItemTranslationEntity.builder()
            .catalogItem(item)
            .localeCode(locale)
            .title(title)
            .groupName(groupName)
            .build();
    }
}
