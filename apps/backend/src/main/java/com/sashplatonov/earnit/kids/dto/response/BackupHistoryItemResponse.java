package com.sashplatonov.earnit.kids.dto.response;

import java.time.Instant;

public record BackupHistoryItemResponse(
    String filename,
    long sizeBytes,
    Instant createdAt
) { }