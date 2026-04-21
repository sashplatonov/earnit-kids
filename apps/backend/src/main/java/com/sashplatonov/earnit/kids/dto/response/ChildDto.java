package com.sashplatonov.earnit.kids.dto.response;

import java.util.List;

public record ChildDto(
    int id,
    String name,
    int balance,
    int monthlyLimit,
    int dailyCoinLimit,
    String theme,
    List<String> taskGroupOrder,
    List<String> shopGroupOrder,
    List<String> childTaskGroupOrder,
    List<String> childShopGroupOrder
) { }
