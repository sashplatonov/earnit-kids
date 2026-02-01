package com.coinsshop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Task(
        String id,
        String title,
        int reward,
        String icon) {
}
