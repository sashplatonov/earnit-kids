package com.sashplatonov.earnit.kids.repository;

import com.fasterxml.jackson.databind.JsonNode;

public record TaskUpsertCommand(
    int familyDbId,
    int childId,
    long taskId,
    String name,
    int coins,
    String groupName,
    JsonNode frequency,
    String comment,
    Integer moneyLimit,
    boolean active,
    boolean deleted
) {
}
