package com.sashplatonov.earnit.kids.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record TokenResponse(@Schema(format = "password") String token) { }
