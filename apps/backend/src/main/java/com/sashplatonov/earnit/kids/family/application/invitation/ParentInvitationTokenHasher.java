package com.sashplatonov.earnit.kids.family.application.invitation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@ApplicationScoped
public class ParentInvitationTokenHasher {
    private final String activeKeyId;
    private final String activeKey;
    private final String previousKeyId;
    private final String previousKey;

    @Inject
    public ParentInvitationTokenHasher(ParentInvitationTokenConfig config) {
        this(config.activeKeyId(), config.activeKey(), config.previousKeyId().orElse(null),
            config.previousKey().orElse(null));
    }

    public ParentInvitationTokenHasher(String activeKeyId, String activeKey,
                                       String previousKeyId, String previousKey) {
        this.activeKeyId = activeKeyId;
        this.activeKey = activeKey;
        this.previousKeyId = previousKeyId;
        this.previousKey = previousKey;
    }

    public String activeKeyId() {
        validateConfiguration();
        return activeKeyId;
    }

    public String digest(String rawToken, String keyId) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Parent invitation token is required");
        }
        String secret = secretFor(keyId);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Parent invitation token hashing is unavailable", ex);
        }
    }

    public List<String> verificationKeyIds() {
        validateConfiguration();
        List<String> keyIds = new ArrayList<>();
        keyIds.add(activeKeyId);
        if (previousKeyId != null && !previousKeyId.equals(activeKeyId)) {
            keyIds.add(previousKeyId);
        }
        return List.copyOf(keyIds);
    }

    private String secretFor(String keyId) {
        if (activeKeyId.equals(keyId)) {
            return activeKey;
        }
        if (previousKeyId != null && previousKeyId.equals(keyId)) {
            return previousKey;
        }
        throw new IllegalArgumentException("Unknown parent invitation key identifier");
    }

    private void validateConfiguration() {
        if (activeKeyId == null || activeKeyId.isBlank()) {
            throw new IllegalStateException("Parent invitation active key id must be configured");
        }
        if (activeKey == null || activeKey.isBlank()) {
            throw new IllegalStateException("Parent invitation active key must be configured");
        }
        if ((previousKeyId == null) != (previousKey == null)
            || (previousKeyId != null && previousKeyId.isBlank())
            || (previousKey != null && previousKey.isBlank())) {
            throw new IllegalStateException("Previous parent invitation key configuration is incomplete");
        }
    }
}
