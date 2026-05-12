package com.sashplatonov.earnit.kids.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class GoogleIdentityVerifier {
    public Optional<GoogleIdentity> verify(String credential, String clientId) {
        if (credential == null || credential.isBlank() || clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();

            var idToken = verifier.verify(credential);
            if (idToken == null) {
                return Optional.empty();
            }

            var payload = idToken.getPayload();
            String email = payload.getEmail();
            Object emailVerifiedValue = payload.get("email_verified");
            boolean emailVerified = emailVerifiedValue instanceof Boolean value
                ? value
                : Boolean.parseBoolean(String.valueOf(emailVerifiedValue));

            if (email == null || email.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new GoogleIdentity(email, emailVerified));
        } catch (GeneralSecurityException | IOException ex) {
            log.warn("Google token verification failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
