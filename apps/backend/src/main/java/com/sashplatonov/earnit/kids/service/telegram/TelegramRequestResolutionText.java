package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.RequestResolutionStatus;

final class TelegramRequestResolutionText {
    private TelegramRequestResolutionText() {
    }

    static boolean isFinal(PurchaseRequestStatus status) {
        return status == PurchaseRequestStatus.approved
            || status == PurchaseRequestStatus.rejected
            || status == PurchaseRequestStatus.cancelled;
    }

    static String resolvedTextFor(PurchaseRequestEntity request) {
        RequestResolutionStatus status = switch (request.getStatus()) {
            case approved -> RequestResolutionStatus.approved;
            case rejected -> RequestResolutionStatus.rejected;
            case cancelled -> RequestResolutionStatus.cancelled;
            case pending -> throw new IllegalStateException("Pending request has no resolution status");
        };
        return TelegramCopy.requestResolved(request.getTaskName(), status);
    }
}
