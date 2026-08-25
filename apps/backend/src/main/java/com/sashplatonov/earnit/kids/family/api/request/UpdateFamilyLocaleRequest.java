package com.sashplatonov.earnit.kids.family.api.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateFamilyLocaleRequest(@NotBlank String locale) {
}
