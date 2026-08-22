package com.sashplatonov.earnit.kids.family.api.response;

import java.util.List;

public record ChildDto(
    int id,
    String name,
    int balance,
    int monthlyLimit,
    int dailyCoinLimit,
    int dailyRewardLimit,
    String theme,
    List<String> taskGroupOrder,
    List<String> shopGroupOrder,
    List<String> childTaskGroupOrder,
    List<String> childShopGroupOrder,
    List<String> hiddenTaskGroups,
    List<String> hiddenShopGroups,
    String status
) {
    public ChildDto {
        taskGroupOrder = taskGroupOrder == null ? List.of() : List.copyOf(taskGroupOrder);
        shopGroupOrder = shopGroupOrder == null ? List.of() : List.copyOf(shopGroupOrder);
        childTaskGroupOrder = childTaskGroupOrder == null ? List.of() : List.copyOf(childTaskGroupOrder);
        childShopGroupOrder = childShopGroupOrder == null ? List.of() : List.copyOf(childShopGroupOrder);
        hiddenTaskGroups = hiddenTaskGroups == null ? List.of() : List.copyOf(hiddenTaskGroups);
        hiddenShopGroups = hiddenShopGroups == null ? List.of() : List.copyOf(hiddenShopGroups);
        status = status == null || status.isBlank() ? "ACTIVE" : status;
    }

    public ChildDto(int id,
                    String name,
                    int balance,
                    int monthlyLimit,
                    int dailyCoinLimit,
                    String theme,
                    List<String> taskGroupOrder,
                    List<String> shopGroupOrder,
                    List<String> childTaskGroupOrder,
                    List<String> childShopGroupOrder) {
        this(id, name, balance, monthlyLimit, dailyCoinLimit, 0, theme, taskGroupOrder, shopGroupOrder,
            childTaskGroupOrder, childShopGroupOrder, List.of(), List.of(), "ACTIVE");
    }
}
