package com.sashplatonov.earnit.kids.family.api.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record ChildInfo(int id, String name, @Schema(format = "password") String token) { }
