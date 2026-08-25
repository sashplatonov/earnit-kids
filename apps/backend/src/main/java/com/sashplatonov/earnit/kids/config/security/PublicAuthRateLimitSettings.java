package com.sashplatonov.earnit.kids.config.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class PublicAuthRateLimitSettings {
  @ConfigProperty(name = "app.security.rate-limit.enabled", defaultValue = "true")
  boolean enabled;
  @ConfigProperty(name = "app.security.rate-limit.child.max-requests", defaultValue = "5")
  int childMaxRequests;
  @ConfigProperty(name = "app.security.rate-limit.child.window-seconds", defaultValue = "60")
  long childWindowSeconds;
  @ConfigProperty(name = "app.security.rate-limit.parent.max-requests", defaultValue = "5")
  int parentMaxRequests;
  @ConfigProperty(name = "app.security.rate-limit.parent.window-seconds", defaultValue = "60")
  long parentWindowSeconds;
  @ConfigProperty(name = "app.security.rate-limit.oauth-start.max-requests", defaultValue = "10")
  int oauthStartMaxRequests;
  @ConfigProperty(name = "app.security.rate-limit.oauth-start.window-seconds", defaultValue = "60")
  long oauthStartWindowSeconds;
  @ConfigProperty(name = "app.security.rate-limit.oauth-callback.max-requests", defaultValue = "5")
  int oauthCallbackMaxRequests;
  @ConfigProperty(name = "app.security.rate-limit.oauth-callback.window-seconds", defaultValue = "60")
  long oauthCallbackWindowSeconds;
  @ConfigProperty(name = "app.security.rate-limit.telegram.max-requests", defaultValue = "10")
  int telegramMaxRequests;
  @ConfigProperty(name = "app.security.rate-limit.telegram.window-seconds", defaultValue = "60")
  long telegramWindowSeconds;
}
