package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TelegramFeatureGate {
    private final TelegramConfig config;

    @Inject
    public TelegramFeatureGate(TelegramConfig config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return isMiniAppConfigured();
    }

    public boolean isMiniAppEnabled(String familyId) {
        return isMiniAppConfigured() && isFamilyAllowed(familyId);
    }

    private boolean isMiniAppConfigured() {
        return (config.enabled() || config.miniAppEnabled())
            && config.botToken().filter(token -> !token.isBlank()).isPresent()
            && config.miniAppUrl().filter(url -> !url.isBlank()).isPresent();
    }

    public boolean isBotEnabled() {
        return isBotConfigured();
    }

    public boolean isBotEnabled(String familyId) {
        return isBotConfigured() && isFamilyAllowed(familyId);
    }

    private boolean isBotConfigured() {
        return config.botEnabled()
            && config.botToken().filter(token -> !token.isBlank()).isPresent()
            && config.webhookSecret().filter(secret -> !secret.isBlank()).isPresent()
            && config.miniAppUrl().filter(url -> !url.isBlank()).isPresent();
    }

    public boolean areNotificationsEnabled(String familyId) {
        return config.notificationsEnabled()
            && config.botToken().filter(token -> !token.isBlank()).isPresent()
            && isFamilyAllowed(familyId);
    }

    public boolean hasRolloutRestriction() {
        return config.rolloutFamilyId().filter(id -> !id.isBlank()).isPresent();
    }

    private boolean isFamilyAllowed(String familyId) {
        return config.rolloutFamilyId().filter(id -> !id.isBlank())
            .map(id -> id.equals(familyId))
            .orElse(true);
    }
}
