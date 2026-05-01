package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.Size;

// EXPLAIN: Optional payload for child request submission.
// EXPLAIN: Single-line, up to 120 characters.
public record CreateRequestNoteRequest(
    @Size(max = 120, message = "{validation.request.note.max}")
    String note
) { }
