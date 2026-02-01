package com.coinsshop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Request(
        long id,
        long taskId,
        String taskName,
        int coins,
        String date,
        String status) {
}
