package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;

import java.util.List;

record FamilyDashboardScopeData(
    int familyDbId,
    String rules,
    ChildEntity activeChild,
    List<ChildEntity> visibleChildren,
    Integer resolvedLastSelectedChildId
) {
    static FamilyDashboardScopeData empty(int familyDbId, String rules) {
        return new FamilyDashboardScopeData(familyDbId, rules, null, List.of(), null);
    }
}
