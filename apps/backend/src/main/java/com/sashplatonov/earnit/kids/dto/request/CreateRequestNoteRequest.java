package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.Size;

public record CreateRequestNoteRequest(
    @Size(max = 120, message = "{validation.request.note.max}")
    String note
) { }
