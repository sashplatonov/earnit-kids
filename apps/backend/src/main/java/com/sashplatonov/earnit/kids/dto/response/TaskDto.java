package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskDto(
    long id,
    String name,
    int coins,
    String groupName,
    String icon,
    Object frequency,
    String comment,
    String cueWhen,
    String cueAction,
    Integer moneyLimit,
    boolean isActive,
    int childId,
    String lastCompletedAt,
    TaskPeriodProgressDto periodProgress,
    Long sourceCatalogItemId
) {
    // EXPLAIN: Convenience constructor preserving the pre-icon call signature.
    public TaskDto(long id, String name, int coins, String groupName, Object frequency,
                   String comment, String cueWhen, String cueAction, Integer moneyLimit,
                   boolean isActive, int childId, String lastCompletedAt,
                   TaskPeriodProgressDto periodProgress) {
        this(id, name, coins, groupName, null, frequency, comment, cueWhen, cueAction,
            moneyLimit, isActive, childId, lastCompletedAt, periodProgress, null);
    }
}
