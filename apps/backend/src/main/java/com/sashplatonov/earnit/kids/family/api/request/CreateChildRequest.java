package com.sashplatonov.earnit.kids.family.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChildRequest(
    @NotBlank(message = "{validation.child.name.required}")
    @Size(max = 50, message = "{validation.child.name.max}")
    String name
) { }
