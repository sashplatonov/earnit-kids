package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskDto(
    long id,
    String name,
    int coins,
    String groupName,
    Object frequency,
    String comment,
    Integer moneyLimit,
    int childId
) { }
