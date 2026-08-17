package com.sashplatonov.earnit.kids.domain.model;

public enum ApplicationOutboxEventType {
    TASK_REQUEST_CREATED,
    REWARD_REQUEST_CREATED,
    TASK_APPROVED,
    TASK_REJECTED,
    REWARD_PURCHASED,
    REWARD_APPROVED,
    REWARD_REJECTED,
    BALANCE_ADJUSTED,
    REQUEST_RESOLVED
}
