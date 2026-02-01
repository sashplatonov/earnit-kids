package com.coinsshop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShopItem(
    long id,
    String name,
    int price,
    int rsdLimit,
    String type,
    String comment,
    Frequency frequency
) {}
