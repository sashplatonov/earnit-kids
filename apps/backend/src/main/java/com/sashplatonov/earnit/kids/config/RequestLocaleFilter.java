package com.sashplatonov.earnit.kids.config;

import com.sashplatonov.earnit.kids.i18n.BackendLocaleSupport;
import com.sashplatonov.earnit.kids.i18n.RequestLocaleContext;
import com.sashplatonov.earnit.kids.i18n.RequestLocaleHolder;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.Locale;

@Provider
@Priority(Priorities.AUTHENTICATION - 10)
public class RequestLocaleFilter implements ContainerRequestFilter, ContainerResponseFilter {
    public static final String REQUEST_LOCALE_PROPERTY = "request.locale";

    private final RequestLocaleContext requestLocaleContext;

    public RequestLocaleFilter(RequestLocaleContext requestLocaleContext) {
        this.requestLocaleContext = requestLocaleContext;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Locale locale = BackendLocaleSupport.resolveLocale(
            requestContext.getHeaderString("X-App-Locale"),
            requestContext.getHeaderString("Accept-Language")
        );
        RequestLocaleHolder.set(locale);
        requestLocaleContext.setLocale(locale);
        requestContext.setProperty(REQUEST_LOCALE_PROPERTY, BackendLocaleSupport.toLanguageTag(locale));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        requestLocaleContext.clear();
        RequestLocaleHolder.clear();
    }
}
