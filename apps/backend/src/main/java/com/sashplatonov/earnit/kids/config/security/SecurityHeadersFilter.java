package com.sashplatonov.earnit.kids.config.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;

@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
        response.getHeaders().putSingle("X-Frame-Options", "DENY");
        response.getHeaders().putSingle("X-XSS-Protection", "1; mode=block");
        response.getHeaders().putSingle("Referrer-Policy", "no-referrer");
        response.getHeaders().putSingle("Cross-Origin-Resource-Policy", "same-site");
        response.getHeaders().putSingle(
            "Content-Security-Policy",
            "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'");
        response.getHeaders().putSingle(
            "Permissions-Policy",
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=()");
        if (isHttps(request)) {
            response.getHeaders().putSingle(
                "Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
    }

    private boolean isHttps(ContainerRequestContext request) {
        String forwardedProto = request.getHeaderString("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return "https".equalsIgnoreCase(forwardedProto.trim());
        }
        URI requestUri = request.getUriInfo().getRequestUri();
        return requestUri != null && "https".equalsIgnoreCase(requestUri.getScheme());
    }
}
