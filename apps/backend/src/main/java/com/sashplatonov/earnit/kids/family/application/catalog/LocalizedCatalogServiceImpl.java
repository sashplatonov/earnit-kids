package com.sashplatonov.earnit.kids.family.application.catalog;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemEntity;
import com.sashplatonov.earnit.kids.family.domain.model.catalog.CatalogItemType;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.CatalogItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

@ApplicationScoped
public class LocalizedCatalogServiceImpl implements LocalizedCatalogService {
    private static final String TASKS = "tasks";
    private static final String REWARDS = "rewards";

    private final CatalogItemRepository catalogItems;

    @Inject
    public LocalizedCatalogServiceImpl(CatalogItemRepository catalogItems) {
        this.catalogItems = catalogItems;
    }

    @Override
    public Map<String, Object> getBaseData(FamilyLocale locale) {
        FamilyLocale selectedLocale = locale == null ? FamilyLocale.en : locale;
        List<Map<String, Object>> tasks = new ArrayList<>();
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (CatalogItemEntity item : catalogItems.findActiveOrdered()) {
            Map<String, Object> mapped = mapItem(item, selectedLocale);
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

    private Map<String, Object> mapItem(CatalogItemEntity item, FamilyLocale locale) {
        boolean russian = locale == FamilyLocale.ru;
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("id", item.getExternalId());
        mapped.put("title", russian ? item.getTitleRu() : item.getTitleEn());
        mapped.put("comment", russian ? item.getCommentRu() : item.getCommentEn());
        mapped.put("groupKey", item.getGroupKey());
        mapped.put("groupName", russian ? item.getGroupNameRu() : item.getGroupNameEn());
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
