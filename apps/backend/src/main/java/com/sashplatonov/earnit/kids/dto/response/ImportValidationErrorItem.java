package com.sashplatonov.earnit.kids.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportValidationErrorItem(
    int row,
    String field,
    String message
) { }
