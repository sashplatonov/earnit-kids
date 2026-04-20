package com.sashplatonov.earnit.kids.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationResultTest {

    @Test
    void successFactory_createsSuccessfulResult() {
        OperationResult<String> result = OperationResult.success("saved");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(((OperationResult.Success<String>) result).value()).isEqualTo("saved");
    }

    @Test
    void failureFactories_createFailedResultsWithExpectedPayload() {
        OperationResult<String> detailedFailure = OperationResult.failure("bad_request", "Invalid payload");
        OperationResult<String> simpleFailure = OperationResult.failure("Something went wrong");

        assertThat(detailedFailure.isSuccess()).isFalse();
        assertThat(detailedFailure).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<String>) detailedFailure).errorCode()).isEqualTo("bad_request");
        assertThat(((OperationResult.Failure<String>) detailedFailure).message()).isEqualTo("Invalid payload");

        assertThat(simpleFailure.isSuccess()).isFalse();
        assertThat(simpleFailure).isInstanceOf(OperationResult.Failure.class);
        assertThat(((OperationResult.Failure<String>) simpleFailure).errorCode()).isNull();
        assertThat(((OperationResult.Failure<String>) simpleFailure).message()).isEqualTo("Something went wrong");
    }
}