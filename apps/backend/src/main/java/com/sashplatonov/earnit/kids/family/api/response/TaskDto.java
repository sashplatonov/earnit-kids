package com.sashplatonov.earnit.kids.family.api.response;

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
    Long sourceCatalogItemId) {
}
