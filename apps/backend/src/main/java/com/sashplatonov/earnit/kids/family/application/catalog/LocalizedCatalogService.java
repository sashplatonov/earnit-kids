package com.sashplatonov.earnit.kids.family.application.catalog;

import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;

import java.util.Map;

public interface LocalizedCatalogService {
    Map<String, Object> getBaseData(FamilyLocale locale);
}
