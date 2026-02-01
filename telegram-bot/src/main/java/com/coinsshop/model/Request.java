package com.coinsshop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Request(
        String id,
        String type, // "task" | "buy"
        String itemId, // taskId or shopItemId
        String status, // "pending" | "approved" | "rejected"
        long timestamp,
        String details // Optional text
) {
}
