package com.sashplatonov.earnit.kids.service.common;

import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.Map;

// EXPLAIN: Failure factory coupling OperationResult (util) with BackendMessages (i18n), kept in the service layer so util/OperationResult stays free of i18n imports.
public final class ServiceResults {

    private ServiceResults() {
    }

    public static <T> OperationResult<T> failure(String errorCode, String messageKey) {
        return OperationResult.failure(errorCode, BackendMessages.message(messageKey));
    }

    public static <T> OperationResult<T> failure(String errorCode, String messageKey, Map<String, String> variables) {
        return OperationResult.failure(errorCode, BackendMessages.message(messageKey, variables));
    }

    public static <T> OperationResult<T> failure(String messageKey) {
        return OperationResult.failure(BackendMessages.message(messageKey));
    }
}
