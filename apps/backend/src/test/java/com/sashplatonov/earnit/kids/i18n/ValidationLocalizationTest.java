package com.sashplatonov.earnit.kids.i18n;

import com.sashplatonov.earnit.kids.identity.api.request.LoginRequest;
import com.sashplatonov.earnit.kids.shared.api.response.ErrorResponse;
import com.sashplatonov.earnit.kids.exception.ConstraintViolationExceptionMapper;
import com.sashplatonov.earnit.kids.i18n.RequestLocaleHolder;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ValidationLocalizationTest {

    @Inject Validator validator;

    @Test
    void loginValidation_usesRussianValidationBundleWhenCurrentLocaleIsRussian() {
        RequestLocaleHolder.set("ru");

        var violations = validator.validate(new LoginRequest(null, null));
        var mapper = new ConstraintViolationExceptionMapper();
        var response = mapper.toResponse(new ConstraintViolationException(violations));
        var error = (ErrorResponse) response.getEntity();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(error.title()).isEqualTo("Неверный запрос");
        assertThat(error.errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.detail()).contains("Email обязателен");
        assertThat(error.detail()).contains("Пароль обязателен");

        RequestLocaleHolder.clear();
    }
}