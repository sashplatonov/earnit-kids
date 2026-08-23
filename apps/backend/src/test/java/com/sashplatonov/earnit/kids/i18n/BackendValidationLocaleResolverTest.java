package com.sashplatonov.earnit.kids.i18n;

import org.hibernate.validator.spi.messageinterpolation.LocaleResolverContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BackendValidationLocaleResolverTest {
    private final RequestLocaleContext request = new RequestLocaleContext();

    @AfterEach
    void clearLocale() {
        RequestLocaleHolder.clear();
    }

    @Test
    void selectsRequestedSupportedLocale() {
        RequestLocaleHolder.set("ru");
        BackendValidationLocaleResolver resolver = new BackendValidationLocaleResolver(request);
        assertThat(resolver.resolve(context(Locale.ENGLISH, Locale.ENGLISH, Locale.forLanguageTag("ru"))))
            .isEqualTo(Locale.forLanguageTag("ru"));
    }

    @Test
    void fallsBackToDefaultWhenRequestedLocaleIsUnsupported() {
        BackendValidationLocaleResolver resolver = new BackendValidationLocaleResolver(request);
        assertThat(resolver.resolve(context(Locale.ENGLISH, Locale.ENGLISH))).isEqualTo(Locale.ENGLISH);
    }

    private static LocaleResolverContext context(Locale defaultLocale, Locale... supported) {
        return new LocaleResolverContext() {
            @Override public Set<Locale> getSupportedLocales() { return Set.of(supported); }
            @Override public Locale getDefaultLocale() { return defaultLocale; }
        };
    }
}
