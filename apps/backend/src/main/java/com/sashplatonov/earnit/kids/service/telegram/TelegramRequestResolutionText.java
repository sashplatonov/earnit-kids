package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.RequestResolutionStatus;

// EXPLAIN: Maps a persisted request's final status to the resolution status used
// EXPLAIN: in the Telegram edit. Kept separate from the processor so the status
// EXPLAIN: mapping is a single, testable responsibility.
final class TelegramRequestResolutionText {
    private TelegramRequestResolutionText() {
    }

    static boolean isFinal(PurchaseRequestStatus status) {
        return status == PurchaseRequestStatus.approved
            || status == PurchaseRequestStatus.rejected
            || status == PurchaseRequestStatus.cancelled;
    }

    // EXPLAIN: Builds the final status text from a persisted request whose status
    // EXPLAIN: became final between the pre-send check and the send, so the edited
    // EXPLAIN: message reflects the actual resolution.
    static String resolvedTextFor(PurchaseRequestEntity request) {
        RequestResolutionStatus status = switch (request.getStatus()) {
            case approved -> RequestResolutionStatus.approved;
            case rejected -> RequestResolutionStatus.rejected;
            case cancelled -> RequestResolutionStatus.cancelled;
            // EXPLAIN: pending is not a final status, so it can never reach this
            // EXPLAIN: helper (guarded by isFinal). Failing loudly guards against
            // EXPLAIN: a future regression silently rendering "deleted".
            case pending -> throw new IllegalStateException("Pending request has no resolution status");
        };
        return TelegramCopy.requestResolved(request.getTaskName(), status);
    }
}
