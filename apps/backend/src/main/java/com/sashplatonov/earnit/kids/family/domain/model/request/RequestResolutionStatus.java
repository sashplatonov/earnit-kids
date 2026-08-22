package com.sashplatonov.earnit.kids.family.domain.model.request;

// EXPLAIN: Final resolution status carried by a REQUEST_RESOLVED outbox event.
// EXPLAIN: Mirrors PurchaseRequestStatus for persisted requests and adds the
// EXPLAIN: synthetic "deleted" value for requests that were physically removed,
// EXPLAIN: so the Telegram message can still be updated after the entity is gone.
public enum RequestResolutionStatus {
    approved,
    rejected,
    cancelled,
    deleted
}
