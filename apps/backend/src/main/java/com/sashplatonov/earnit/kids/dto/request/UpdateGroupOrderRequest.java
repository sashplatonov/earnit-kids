package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateGroupOrderRequest(
    @NotBlank(message = "Section is required")
    String section,

    List<String> groups
) { }