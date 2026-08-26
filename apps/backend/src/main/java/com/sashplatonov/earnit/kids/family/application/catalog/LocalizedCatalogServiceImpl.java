package com.sashplatonov.earnit.kids.family.application.catalog;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemEntity;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemType;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemTranslationEntity;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.CatalogItemRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.CatalogItemTranslationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Collections;

@ApplicationScoped
public class LocalizedCatalogServiceImpl implements LocalizedCatalogService {
    private static final String TASKS = "tasks";
    private static final String REWARDS = "rewards";

    private final CatalogItemRepository catalogItems;
    private final CatalogItemTranslationRepository translations;

    @Inject
    public LocalizedCatalogServiceImpl(CatalogItemRepository catalogItems,
                                       CatalogItemTranslationRepository translations) {
        this.catalogItems = catalogItems;
        this.translations = translations;
    }

    @Override
    public Map<String, Object> getBaseData(FamilyLocale locale) {
        FamilyLocale selectedLocale = locale == null ? FamilyLocale.en : locale;
        List<CatalogItemEntity> items = catalogItems.findActiveOrdered();
        String localeCode = selectedLocale.name();
        List<Long> itemIds = items.stream().map(CatalogItemEntity::getId).toList();
        List<CatalogItemTranslationEntity> translationRows = translations.findForItemsAndLocales(
            itemIds, List.of(localeCode, FamilyLocale.en.name()));
        Map<Long, Map<String, CatalogItemTranslationEntity>> translationsByItem = translationRows.stream()
            .collect(Collectors.groupingBy(row -> row.getCatalogItem().getId(),
                Collectors.toMap(CatalogItemTranslationEntity::getLocaleCode, Function.identity())));

        List<Map<String, Object>> tasks = new ArrayList<>();
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (CatalogItemEntity item : items) {
            Map<String, CatalogItemTranslationEntity> itemTranslations = translationsByItem
                .getOrDefault(item.getId(), Map.of());
            Map<String, Object> mapped = mapItem(item, resolveTranslation(item, itemTranslations, localeCode));
            if (item.getItemType() == CatalogItemType.task) {
                tasks.add(mapped);
            } else {
                rewards.add(mapped);
            }
        }

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put(TASKS, List.copyOf(tasks));
        catalog.put(REWARDS, List.copyOf(rewards));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(TASKS, List.of());
        result.put("catalog", Map.copyOf(catalog));
        return Map.copyOf(result);
    }

    CatalogItemTranslationEntity resolveTranslation(CatalogItemEntity item,
                                                     Map<String, CatalogItemTranslationEntity> itemTranslations,
                                                     String localeCode) {
        CatalogItemTranslationEntity translation = itemTranslations.get(localeCode);
        if (translation == null) {
            translation = itemTranslations.get(FamilyLocale.en.name());
        }
        if (translation == null) {
            throw new IllegalStateException("Missing English catalog translation for item " + item.getExternalId());
        }
        return translation;
    }

    private Map<String, Object> mapItem(CatalogItemEntity item, CatalogItemTranslationEntity translation) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("id", item.getExternalId());
        mapped.put("title", translation.getTitle());
        mapped.put("comment", translation.getComment());
        mapped.put("groupKey", item.getGroupKey());
        mapped.put("groupName", translation.getGroupName());
        mapped.put("semanticGraphicKey", item.getSemanticGraphicKey());
        mapped.put("frequencyLimit", item.getFrequencyLimit());
        mapped.put("frequencyPeriod", item.getFrequencyPeriod());
        mapped.put("minAge", item.getMinAge());
        mapped.put("maxAge", item.getMaxAge());
        mapped.put("difficulty", item.getDifficulty());
        mapped.put("tags", List.of(item.getGroupKey()));
        mapped.put("active", item.isActive());
        mapped.put("sortOrder", item.getSortOrder());
        if (item.getItemType() == CatalogItemType.task) {
            mapped.put("coins", item.getCoins());
        } else {
            mapped.put("price", item.getPrice());
        }
        return Collections.unmodifiableMap(mapped);
    }
}
