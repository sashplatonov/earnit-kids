package com.sashplatonov.earnit.kids.resource.common;

public record ClientErrorMessage(
    String eventCode,
    String route,
    Integer status,
    String category,
    String traceId,
    String errorClass
) { }