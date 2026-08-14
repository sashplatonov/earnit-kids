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
    List<String> childShopGroupOrder,
    Long rewardGoalItemId,
    String status
) {
    public ChildDto {
        taskGroupOrder = taskGroupOrder == null ? List.of() : List.copyOf(taskGroupOrder);
        shopGroupOrder = shopGroupOrder == null ? List.of() : List.copyOf(shopGroupOrder);
        childTaskGroupOrder = childTaskGroupOrder == null ? List.of() : List.copyOf(childTaskGroupOrder);
        childShopGroupOrder = childShopGroupOrder == null ? List.of() : List.copyOf(childShopGroupOrder);
        status = status == null || status.isBlank() ? "ACTIVE" : status;
    }

    // EXPLAIN: Convenience constructor preserving the pre-status call signature.
    public ChildDto(int id,
                    String name,
                    int balance,
                    int monthlyLimit,
                    int dailyCoinLimit,
                    String theme,
                    List<String> taskGroupOrder,
                    List<String> shopGroupOrder,
                    List<String> childTaskGroupOrder,
                    List<String> childShopGroupOrder,
                    Long rewardGoalItemId) {
        this(id, name, balance, monthlyLimit, dailyCoinLimit, theme, taskGroupOrder, shopGroupOrder,
            childTaskGroupOrder, childShopGroupOrder, rewardGoalItemId, "ACTIVE");
    }
}
