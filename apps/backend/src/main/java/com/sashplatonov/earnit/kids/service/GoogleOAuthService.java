package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.config.AppConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class GoogleOAuthService {
    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    public String buildAuthorizationUrl(String redirectUri, String stateToken) {
        String clientId = appConfig.google().clientId().orElseThrow();
        String scope = URLEncoder.encode("openid email profile", StandardCharsets.UTF_8);
        String url = AUTH_ENDPOINT +
            "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
            "&response_type=code" +
            "&scope=" + scope +
            "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
            "&access_type=offline" +
            "&prompt=consent" +
            "&state=" + URLEncoder.encode(stateToken, StandardCharsets.UTF_8);
        return url;
    }

    public Optional<GoogleTokenResponse> exchangeCode(String code, String redirectUri) {
        try {
            String clientId = appConfig.google().clientId().orElse(null);
            String clientSecret = appConfig.google().clientSecret().orElse(null);
            if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
                log.warn("Google OAuth exchange requested but client id/secret not configured");
                return Optional.empty();
            }

            String body = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&grant_type=authorization_code";

            var req = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_ENDPOINT))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            var client = HttpClient.newBuilder().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Token endpoint returned status {}: {}", resp.statusCode(), resp.body());
                return Optional.empty();
            }

            GoogleTokenResponse tokenResp = objectMapper.readValue(resp.body(), GoogleTokenResponse.class);
            return Optional.of(tokenResp);
        } catch (IOException | InterruptedException ex) {
            log.warn("Failed to exchange Google auth code: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
