package com.sashplatonov.earnit.kids.util;

import com.sashplatonov.earnit.kids.i18n.BackendMessages;

import java.util.Map;

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
