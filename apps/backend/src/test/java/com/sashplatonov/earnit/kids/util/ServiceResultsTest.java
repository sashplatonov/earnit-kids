package com.sashplatonov.earnit.kids.util;

import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceResultsTest {

    @Test
    void failure_withErrorCodeAndKey_producesFailureWithResolvedMessage() {
        OperationResult<String> result = ServiceResults.failure("FAMILY_NOT_FOUND", "family.familyNotFound");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result).isInstanceOf(OperationResult.Failure.class);
        OperationResult.Failure<String> failure = (OperationResult.Failure<String>) result;
        assertThat(failure.errorCode()).isEqualTo("FAMILY_NOT_FOUND");
        assertThat(failure.message()).isEqualTo(BackendMessages.message("family.familyNotFound"));
    }

    @Test
    void failure_withVariables_passesVariablesToMessage() {
        OperationResult<String> result = ServiceResults.failure(
            "UNKNOWN_PREFERENCE", "family.unknownSetting", Map.of("key", "theme"));

        assertThat(result.isSuccess()).isFalse();
        OperationResult.Failure<String> failure = (OperationResult.Failure<String>) result;
        assertThat(failure.errorCode()).isEqualTo("UNKNOWN_PREFERENCE");
        assertThat(failure.message()).isEqualTo(BackendMessages.message("family.unknownSetting", Map.of("key", "theme")));
    }

    @Test
    void failure_withOnlyMessageKey_producesFailureWithNullErrorCode() {
        OperationResult<String> result = ServiceResults.failure("family.familyNotFound");

        assertThat(result.isSuccess()).isFalse();
        OperationResult.Failure<String> failure = (OperationResult.Failure<String>) result;
        assertThat(failure.errorCode()).isNull();
        assertThat(failure.message()).isEqualTo(BackendMessages.message("family.familyNotFound"));
    }
}
