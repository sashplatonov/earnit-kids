package com.sashplatonov.earnit.kids.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "app.telegram")
public interface TelegramConfig {

    @WithDefault("")
    Optional<String> botToken();

    @WithDefault("")
    Optional<String> botUsername();

    @WithDefault("")
    Optional<String> miniAppUrl();

    @WithDefault("")
    Optional<String> publicSiteUrl();

    @WithDefault("300")
    int initDataMaxAgeSeconds();

    @WithDefault("false")
    boolean enabled();

    @WithDefault("false")
    boolean miniAppEnabled();

    @WithDefault("false")
    boolean botEnabled();

    @WithDefault("false")
    boolean notificationsEnabled();

    @WithDefault("")
    Optional<String> rolloutFamilyId();

    @WithDefault("false")
    boolean outboxEnabled();

    @WithDefault("5")
    int outboxMaxAttempts();

    @WithDefault("")
    Optional<String> webhookSecret();

    @WithDefault("")
    Optional<String> callbackSigningSecret();

    @WithDefault("300")
    int callbackTtlSeconds();

    @WithDefault("1")
    int callbackMenuVersion();

    @WithDefault("true")
    boolean retentionEnabled();

    @WithDefault("24h")
    String retentionPollInterval();

    @WithDefault("30")
    int inviteRetentionDays();

    @WithDefault("30")
    int callbackRetentionDays();

    @WithDefault("30")
    int webhookUpdateRetentionDays();

    @WithDefault("30")
    int deliveryRetentionDays();

    @WithDefault("30")
    int outboxRetentionDays();

    @WithDefault("365")
    int auditRetentionDays();

    @WithDefault("100")
    int retentionBatchSize();
}
