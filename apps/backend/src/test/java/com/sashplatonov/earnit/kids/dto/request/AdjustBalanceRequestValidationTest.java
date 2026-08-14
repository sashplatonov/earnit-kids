package com.sashplatonov.earnit.kids.dto.request;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AdjustBalanceRequestValidationTest {

    @Inject
    Validator validator;

    @Test
    void validNegativeAmount_hasNoViolations() {
        var violations = validator.validate(new AdjustBalanceRequest(10, -3, "Manual correction"));
        assertThat(violations).isEmpty();
    }

    @Test
    void zeroAmount_hasNoViolations() {
        var violations = validator.validate(new AdjustBalanceRequest(10, 0, null));
        assertThat(violations).isEmpty();
    }

    @Test
    void amountAboveMax_isRejected() {
        Set<ConstraintViolation<AdjustBalanceRequest>> violations =
            validator.validate(new AdjustBalanceRequest(10, 1_000_001, null));
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Amount cannot exceed 1000000");
    }

    @Test
    void amountBelowMin_isRejected() {
        Set<ConstraintViolation<AdjustBalanceRequest>> violations =
            validator.validate(new AdjustBalanceRequest(10, -1_000_001, null));
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Amount cannot be below -1000000");
    }
}
