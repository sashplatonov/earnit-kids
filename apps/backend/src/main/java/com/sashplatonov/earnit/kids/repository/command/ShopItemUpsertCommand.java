package com.sashplatonov.earnit.kids.repository.command;

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
    boolean deleted,
    String icon,
    Long sourceCatalogItemId
) {
    public ShopItemUpsertCommand(int familyDbId, int childId, long itemId, String name,
                                 int price, String groupName, JsonNode frequency,
                                 String comment, Integer moneyLimit, boolean active,
                                 boolean deleted, String icon) {
        this(familyDbId, childId, itemId, name, price, groupName, frequency, comment,
            moneyLimit, active, deleted, icon, null);
    }

    public ShopItemUpsertCommand(int familyDbId, int childId, long itemId, String name,
                                 int price, String groupName, JsonNode frequency,
                                 String comment, Integer moneyLimit, boolean active,
                                 boolean deleted) {
        this(familyDbId, childId, itemId, name, price, groupName, frequency, comment,
            moneyLimit, active, deleted, null, null);
    }
}
