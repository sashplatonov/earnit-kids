package com.sashplatonov.earnit.kids.family.application.action;

import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.util.OperationResult;

final class FamilyActionRequestSupport {
    private static final int MAX_NOTE_LENGTH = 120;

    private FamilyActionRequestSupport() { }

    static OperationResult<String> normalizeNote(String note) {
        if (note == null || note.trim().isEmpty()) {
            return OperationResult.success(null);
        }
        String trimmed = note.trim();
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            return OperationResult.failure("REQUEST_NOTE_INVALID", BackendMessages.message("requests.noteInvalid"));
        }
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            return OperationResult.failure("REQUEST_NOTE_TOO_LONG", BackendMessages.message("requests.noteTooLong"));
        }
        return OperationResult.success(trimmed);
    }

    static boolean isPending(PurchaseRequestEntity request) {
        return request.getStatus() == null || request.getStatus() == PurchaseRequestStatus.pending;
    }

    static boolean isPurchase(PurchaseRequestEntity request) {
        return request.getRequestType() != null && request.getRequestType().isPurchase();
    }
}
