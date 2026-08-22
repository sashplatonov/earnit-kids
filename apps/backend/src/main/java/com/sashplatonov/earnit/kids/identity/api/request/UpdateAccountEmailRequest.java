package com.sashplatonov.earnit.kids.identity.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountEmailRequest(
    @NotBlank
    @Email
    @Size(max = 254)
    String newEmail
) {
}
