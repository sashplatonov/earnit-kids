package com.sashplatonov.earnit.kids.dto.response;

public record ChildDto(
    int id,
    String name,
    int balance,
    int monthlyLimit,
    int dailyCoinLimit,
    String theme
) { }
