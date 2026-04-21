package com.sashplatonov.earnit.kids.config;

import com.sashplatonov.earnit.kids.i18n.RequestLocaleHolder;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLocaleFilterTest {

    private final RequestLocaleFilter filter = new RequestLocaleFilter();

    @AfterEach
    void tearDown() {
        RequestLocaleHolder.clear();
    }

    @Test
    void filter_prefersExplicitAppLocaleHeader() {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        when(requestContext.getHeaderString("X-App-Locale")).thenReturn("ru");
        when(requestContext.getHeaderString("Accept-Language")).thenReturn("en-US,en;q=0.9");

        filter.filter(requestContext);

        verify(requestContext).setProperty(RequestLocaleFilter.REQUEST_LOCALE_PROPERTY, "ru");
        assertThat(RequestLocaleHolder.get()).isEqualTo("ru");
    }

    @Test
    void filter_fallsBackToAcceptLanguageAndClearsAfterResponse() {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        ContainerResponseContext responseContext = mock(ContainerResponseContext.class);
        when(requestContext.getHeaderString("X-App-Locale")).thenReturn(null);
        when(requestContext.getHeaderString("Accept-Language")).thenReturn("ru-RU,ru;q=0.9,en;q=0.8");

        filter.filter(requestContext);
        assertThat(RequestLocaleHolder.get()).isEqualTo("ru");

        filter.filter(requestContext, responseContext);
        assertThat(RequestLocaleHolder.get()).isEqualTo("en");
    }
}
