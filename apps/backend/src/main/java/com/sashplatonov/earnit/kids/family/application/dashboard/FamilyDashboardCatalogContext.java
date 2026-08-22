package com.sashplatonov.earnit.kids.family.application.dashboard;

import com.sashplatonov.earnit.kids.family.api.response.ShopItemDto;
import com.sashplatonov.earnit.kids.family.api.response.TaskDto;

import java.util.List;
import java.util.Map;

record FamilyDashboardCatalogContext(
    List<TaskDto> tasks,
    List<ShopItemDto> shopItems,
    Map<Long, TaskDto> taskMap,
    Map<Long, ShopItemDto> shopMap
) { }
