package com.sashplatonov.earnit.kids.repository;

import com.fasterxml.jackson.databind.JsonNode;

public record ShopItemUpsertCommand(
    int familyDbId,
    int childId,
    long itemId,
    String name,
    int price,
    String groupName,
    JsonNode frequency,
    String comment,
    Integer moneyLimit,
    boolean active,
    boolean deleted
) {
}
