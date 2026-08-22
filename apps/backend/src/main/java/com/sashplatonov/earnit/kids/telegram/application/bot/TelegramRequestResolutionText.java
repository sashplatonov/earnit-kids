package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.family.domain.model.request.RequestResolutionStatus;

public final class TelegramRequestResolutionText {
    private TelegramRequestResolutionText() {
    }

    public static boolean isFinal(PurchaseRequestStatus status) {
        return status == PurchaseRequestStatus.approved
            || status == PurchaseRequestStatus.rejected
            || status == PurchaseRequestStatus.cancelled;
    }

    public static String resolvedTextFor(PurchaseRequestEntity request) {
        RequestResolutionStatus status = switch (request.getStatus()) {
            case approved -> RequestResolutionStatus.approved;
            case rejected -> RequestResolutionStatus.rejected;
            case cancelled -> RequestResolutionStatus.cancelled;
            case pending -> throw new IllegalStateException("Pending request has no resolution status");
        };
        return TelegramCopy.requestResolved(request.getTaskName(), status);
    }
}
