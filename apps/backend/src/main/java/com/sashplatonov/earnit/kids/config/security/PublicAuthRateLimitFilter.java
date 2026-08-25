package com.sashplatonov.earnit.kids.config.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.net.URI;

@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class PublicAuthRateLimitFilter implements ContainerRequestFilter {
  private final RateLimitAcquirer limiter;
  private final boolean enabled;
  private final int childLimit;
  private final long childWindowSeconds;
  private final int parentLimit;
  private final long parentWindowSeconds;
  private final int oauthStartLimit;
  private final long oauthStartWindowSeconds;
  private final int oauthCallbackLimit;
  private final long oauthCallbackWindowSeconds;
  private final int telegramLimit;
  private final long telegramWindowSeconds;

  @Inject
  public PublicAuthRateLimitFilter(InboundRateLimiter limiter, PublicAuthRateLimitSettings config) {
    this.limiter = limiter::tryAcquire;
    this.enabled = config.enabled;
    this.childLimit = config.childMaxRequests;
    this.childWindowSeconds = config.childWindowSeconds;
    this.parentLimit = config.parentMaxRequests;
    this.parentWindowSeconds = config.parentWindowSeconds;
    this.oauthStartLimit = config.oauthStartMaxRequests;
    this.oauthStartWindowSeconds = config.oauthStartWindowSeconds;
    this.oauthCallbackLimit = config.oauthCallbackMaxRequests;
    this.oauthCallbackWindowSeconds = config.oauthCallbackWindowSeconds;
    this.telegramLimit = config.telegramMaxRequests;
    this.telegramWindowSeconds = config.telegramWindowSeconds;
  }

  PublicAuthRateLimitFilter(
      InboundRateLimiter limiter, boolean enabled, int limit, long windowSeconds) {
    this.limiter = limiter::tryAcquire;
    this.enabled = enabled;
    this.childLimit = limit;
    this.childWindowSeconds = windowSeconds;
    this.parentLimit = limit;
    this.parentWindowSeconds = windowSeconds;
    this.oauthStartLimit = limit;
    this.oauthStartWindowSeconds = windowSeconds;
    this.oauthCallbackLimit = limit;
    this.oauthCallbackWindowSeconds = windowSeconds;
    this.telegramLimit = limit;
    this.telegramWindowSeconds = windowSeconds;
  }

  @Override
  public void filter(ContainerRequestContext request) throws IOException {
    if (!enabled) {
      return;
    }
    String route = route(request);
    if (route == null) {
      return;
    }
    RateLimitDecision decision =
        limiter.tryAcquire(route, clientAddress(request), limit(route), window(route));
    if (!decision.allowed()) {
      request.abortWith(
          Response.status(Response.Status.TOO_MANY_REQUESTS)
              .header("Retry-After", decision.retryAfterSeconds())
              .header("Cache-Control", "no-store")
              .entity("Authentication temporarily unavailable. Please retry later.")
              .build());
    }
  }

  private String route(ContainerRequestContext request) {
    String path = request.getUriInfo().getPath();
    String method = request.getMethod();
    if ("GET".equals(method) && path.matches("/?login-child/[^/]+/?")) {
      return "child-magic-link";
    }
    if ("GET".equals(method) && path.matches("/?invite/parent/[^/]+/?")) {
      return "parent-invitation";
    }
    if ("GET".equals(method) && "/api/login-google/url".equals(normalize(path))) {
      return "oauth-start";
    }
    if ("GET".equals(method) && "/api/login-google/callback".equals(normalize(path))) {
      return "oauth-callback";
    }
    if ("POST".equals(method) && "/api/telegram/auth/exchange".equals(normalize(path))) {
      return "telegram-auth-exchange";
    }
    return null;
  }

  private String clientAddress(ContainerRequestContext request) {
    String forwarded = request.getHeaderString("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",", 2)[0].trim();
    }
    URI uri = request.getUriInfo().getRequestUri();
    return uri == null || uri.getHost() == null ? "unknown" : uri.getHost();
  }

  private int limit(String route) {
    return switch (route) {
      case "child-magic-link" -> childLimit;
      case "parent-invitation" -> parentLimit;
      case "oauth-start" -> oauthStartLimit;
      case "oauth-callback" -> oauthCallbackLimit;
      case "telegram-auth-exchange" -> telegramLimit;
      default -> throw new IllegalArgumentException("Unknown public auth route");
    };
  }

  private long window(String route) {
    return switch (route) {
      case "child-magic-link" -> childWindowSeconds;
      case "parent-invitation" -> parentWindowSeconds;
      case "oauth-start" -> oauthStartWindowSeconds;
      case "oauth-callback" -> oauthCallbackWindowSeconds;
      case "telegram-auth-exchange" -> telegramWindowSeconds;
      default -> throw new IllegalArgumentException("Unknown public auth route");
    };
  }

  private String normalize(String path) {
    if (path == null || path.isBlank()) {
      return "/";
    }
    return path.startsWith("/") ? path : "/" + path;
  }
}
