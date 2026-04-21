package com.sashplatonov.earnit.kids.config;

import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.i18n.RequestLocaleHolder;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RequestLocaleFilter implements ContainerRequestFilter, ContainerResponseFilter {
    public static final String REQUEST_LOCALE_PROPERTY = "request.locale";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String locale = BackendMessages.resolveLocale(
            requestContext.getHeaderString("X-App-Locale"),
            requestContext.getHeaderString("Accept-Language")
        );
        RequestLocaleHolder.set(locale);
        requestContext.setProperty(REQUEST_LOCALE_PROPERTY, locale);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        RequestLocaleHolder.clear();
    }
}
