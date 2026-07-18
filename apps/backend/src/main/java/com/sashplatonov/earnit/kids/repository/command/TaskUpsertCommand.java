package com.sashplatonov.earnit.kids.repository.command;

import com.fasterxml.jackson.databind.JsonNode;

public record TaskUpsertCommand(
    int familyDbId,
    int childId,
    long taskId,
    TaskContentCommand content,
    JsonNode frequency,
    Integer moneyLimit,
    boolean active,
    boolean deleted
) {
}
