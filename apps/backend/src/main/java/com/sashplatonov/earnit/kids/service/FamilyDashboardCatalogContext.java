package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;

import java.util.List;
import java.util.Map;

record FamilyDashboardCatalogContext(
    List<TaskDto> tasks,
    List<ShopItemDto> shopItems,
    Map<Long, TaskDto> taskMap,
    Map<Long, ShopItemDto> shopMap
) { }
