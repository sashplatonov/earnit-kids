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
    Optional<String> adminUserIds();

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

    // EXPLAIN: UX-01 — bump this to force a one-time ReplyKeyboardRemove for
    // EXPLAIN: every identity whose stored version is behind, clearing any
    // EXPLAIN: stale cached reply keyboard on the Telegram client.
    // EXPLAIN: Version 2 — bottom row switched to plain-text MiniApp + Сайт.
    // EXPLAIN: Version 3 — MiniApp button removed; only Сайт remains.
    @WithDefault("3")
    int replyKeyboardVersion();

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
