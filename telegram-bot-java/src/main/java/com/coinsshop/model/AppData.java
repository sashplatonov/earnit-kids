package com.coinsshop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AppData(
    String pin,
    int balance,
    List<Task> tasks,
    List<ShopItem> shop,
    List<Request> requests
) {}
