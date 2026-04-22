package com.sashplatonov.earnit.kids.i18n;

import jakarta.enterprise.context.RequestScoped;

import java.util.Locale;

@RequestScoped
public class RequestLocaleContext {
    private Locale locale = BackendLocaleSupport.defaultLocale();

    public Locale getLocale() {
        return locale;
    }

    public String getLanguageTag() {
        return BackendLocaleSupport.toLanguageTag(locale);
    }

    public void setLocale(Locale locale) {
        this.locale = BackendLocaleSupport.supportedOrDefault(locale);
    }

    public void clear() {
        locale = BackendLocaleSupport.defaultLocale();
    }
}