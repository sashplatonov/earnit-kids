package com.coinsshop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Transaction(
        String id,
        String date,
        String description,
        int amount,
        String type // "earn" or "spend"
) {
}
