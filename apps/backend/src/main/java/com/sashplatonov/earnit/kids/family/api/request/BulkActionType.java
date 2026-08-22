package com.sashplatonov.earnit.kids.family.api.request;

import java.util.Optional;

public enum BulkActionType {
    delete,
    block,
    unblock,
    change_group;

    public static Optional<BulkActionType> fromWireValue(String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }
        String normalizedValue = rawValue.trim().toLowerCase();
        try {
            return Optional.of(BulkActionType.valueOf(normalizedValue));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
