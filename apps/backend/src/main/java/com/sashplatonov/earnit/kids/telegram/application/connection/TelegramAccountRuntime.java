package com.sashplatonov.earnit.kids.telegram.application.connection;

import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public final class TelegramAccountRuntime {
  private final TelegramConfig config;
  private final TelegramFeatureGate featureGate;

  @Inject
  public TelegramAccountRuntime(TelegramConfig config, TelegramFeatureGate featureGate) {
    this.config = config;
    this.featureGate = featureGate;
  }

  TelegramConfig config() {
    return config;
  }

  TelegramFeatureGate featureGate() {
    return featureGate;
  }
}
