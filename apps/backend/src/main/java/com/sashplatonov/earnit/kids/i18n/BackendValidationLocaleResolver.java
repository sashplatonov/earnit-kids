package com.sashplatonov.earnit.kids.i18n;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import org.hibernate.validator.spi.messageinterpolation.LocaleResolver;
import org.hibernate.validator.spi.messageinterpolation.LocaleResolverContext;

import java.util.Locale;

@ApplicationScoped
@Priority(1000)
public class BackendValidationLocaleResolver implements LocaleResolver {

    private final RequestLocaleContext requestLocaleContext;

    public BackendValidationLocaleResolver(RequestLocaleContext requestLocaleContext) {
        this.requestLocaleContext = requestLocaleContext;
    }

    @Override
    public Locale resolve(LocaleResolverContext context) {
        Locale requestedLocale = currentLocale();
        for (Locale supportedLocale : context.getSupportedLocales()) {
            if (BackendLocaleSupport.supportedOrDefault(supportedLocale).equals(requestedLocale)) {
                return supportedLocale;
            }
        }
        return context.getDefaultLocale();
    }

    private Locale currentLocale() {
        Locale holderLocale = RequestLocaleHolder.getLocale();
        if (!BackendLocaleSupport.defaultLocale().equals(holderLocale)) {
            return holderLocale;
        }

        try {
            return BackendLocaleSupport.supportedOrDefault(requestLocaleContext.getLocale());
        } catch (ContextNotActiveException ex) {
            return RequestLocaleHolder.getLocale();
        }
    }
}