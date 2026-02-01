package com.coinsshop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShopItem(
        String id,
        String title,
        int price,
        String icon) {
}
