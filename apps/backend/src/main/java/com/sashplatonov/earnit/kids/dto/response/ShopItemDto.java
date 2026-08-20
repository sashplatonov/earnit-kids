package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShopItemDto(
    long id,
    String name,
    int price,
    String groupName,
    String icon,
    Object frequency,
    String comment,
    Integer moneyLimit,
    boolean isActive,
    int childId,
    String lastPurchasedAt,
    Long sourceCatalogItemId,
    TaskPeriodProgressDto periodProgress
) {
    public ShopItemDto(long id, String name, int price, String groupName, Object frequency,
                       String comment, Integer moneyLimit, boolean isActive, int childId,
                       String lastPurchasedAt) {        this(id, name, price, groupName, null, frequency, comment, moneyLimit, isActive,
            childId, lastPurchasedAt, null, null);
    }
}
