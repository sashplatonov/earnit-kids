package com.coinsshop.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Task(
    long id,
    String name,
    int coins,
    String comment,
    Frequency frequency
) {}
