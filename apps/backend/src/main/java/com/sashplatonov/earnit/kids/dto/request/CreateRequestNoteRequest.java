package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Optional payload for child request submission.
 * Single-line, up to 120 characters.
 */
public record CreateRequestNoteRequest(
    @Size(max = 120, message = "{validation.request.note.max}")
    String note
) { }
