package com.sashplatonov.earnit.kids.util;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;

public final class PublicOriginResolver {
    private final String configuredAppUrl;

    public PublicOriginResolver(String configuredAppUrl) {
        this.configuredAppUrl = normalizeOrigin(configuredAppUrl);
    }

    public String toAbsoluteRedirect(String redirect, ContainerRequestContext requestContext) {
        String normalizedRedirect = normalizeRedirect(redirect);
        if (isAbsolute(normalizedRedirect)) {
            return normalizedRedirect;
        }

        String origin = configuredOrForwardedOrigin(requestContext);
        if (origin == null) {
            return normalizedRedirect;
        }
        return origin + normalizedRedirect;
    }

    public String resolveAbsoluteAppUri(String path, ContainerRequestContext requestContext) {
        String normalizedPath = normalizeRedirect(path);
        if (isAbsolute(normalizedPath)) {
            return normalizedPath;
        }

        String origin = configuredOrForwardedOrigin(requestContext);
        if (origin == null) {
            origin = requestOrigin(requestContext);
        }
        if (origin == null) {
            return normalizedPath;
        }
        return origin + normalizedPath;
    }

    private String configuredOrForwardedOrigin(ContainerRequestContext requestContext) {
        if (configuredAppUrl != null) {
            return configuredAppUrl;
        }
        return forwardedOrigin(requestContext);
    }

    private String forwardedOrigin(ContainerRequestContext requestContext) {
        if (requestContext == null) {
            return null;
        }

        String forwardedProto = firstHeaderValue(requestContext.getHeaderString("X-Forwarded-Proto"));
        String forwardedHost = firstHeaderValue(requestContext.getHeaderString("X-Forwarded-Host"));
        if (forwardedProto == null || forwardedHost == null) {
            return null;
        }

        return normalizeOrigin(forwardedProto + "://" + forwardedHost);
    }

    private String requestOrigin(ContainerRequestContext requestContext) {
        if (requestContext == null) {
            return null;
        }

        UriInfo uriInfo = requestContext.getUriInfo();
        if (uriInfo == null) {
            return null;
        }

        URI requestUri = uriInfo.getRequestUri();
        if (requestUri == null || requestUri.getScheme() == null || requestUri.getHost() == null) {
            return null;
        }

        String origin = requestUri.getScheme() + "://" + requestUri.getHost();
        int port = requestUri.getPort();
        if (port >= 0 && !isDefaultPort(requestUri.getScheme(), port)) {
            origin = origin + ":" + port;
        }
        return origin;
    }

    private static String normalizeOrigin(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String normalizeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return "/";
        }
        if (isAbsolute(redirect)) {
            return redirect;
        }
        if (redirect.startsWith("/")) {
            return redirect;
        }
        return "/" + redirect;
    }

    private static String firstHeaderValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.split(",")[0].trim();
    }

    private static boolean isAbsolute(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
            || ("https".equalsIgnoreCase(scheme) && port == 443);
    }
}
