package com.sashplatonov.earnit.kids.telegram.config;

import io.smallrye.config.WithDefault;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "app.telegram")
public interface TelegramRetentionConfig {
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
