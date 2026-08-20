package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.repository.TelegramCallbackActionRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Arrays;
import java.util.Optional;
import java.util.HexFormat;

@ApplicationScoped
public class TelegramCallbackService {
    private static final int CALLBACK_SIGNATURE_BYTES = 15;
    @Inject private TelegramConfig config;
    @Inject private TelegramIdentityRepository identities;
    @Inject private TelegramCallbackActionRepository callbacks;
    @Inject private TimeProvider timeProvider;
    @Inject private TelegramIdentityService identityService;

    TelegramCallbackService() {
    }

    TelegramCallbackService(TelegramConfig config,
                            TelegramIdentityRepository identities,
                            TelegramCallbackActionRepository callbacks,
                            TimeProvider timeProvider,
                            TelegramIdentityService identityService) {
        this.config = config;
        this.identities = identities;
        this.callbacks = callbacks;
        this.timeProvider = timeProvider;
        this.identityService = identityService;
    }

    public Optional<VerifiedCallback> verifyNavigation(String data, long telegramUserId) {
        String[] parts = data == null ? new String[0] : data.split("\\.", -1);
        if (parts.length != 5 || !"nav".equals(parts[0])) {
            return Optional.empty();
        }
        try {
            long issuedAt = Long.parseLong(parts[2]);
            int version = Integer.parseInt(parts[3]);
            if (parts[1].isBlank() || version != config.callbackMenuVersion()
                || issuedAt > timeProvider.currentEpochSecond()
                || timeProvider.currentEpochSecond() - issuedAt > config.callbackTtlSeconds()) {
                return Optional.empty();
            }
            String canonical = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            if (!matches(canonical, parts[4])) {
                return Optional.empty();
            }
            return Optional.of(new VerifiedCallback(TelegramCallbackActionCodec.expand(parts[1]), telegramUserId,
                Instant.ofEpochSecond(issuedAt)));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public String signNavigation(String action) {
        if (action == null || action.isBlank() || action.contains(".")) {
            throw new IllegalArgumentException("Invalid navigation action");
        }
        long issuedAt = timeProvider.currentEpochSecond();
        String canonical = String.join(".", "nav", TelegramCallbackActionCodec.compact(action), Long.toString(issuedAt),
            Integer.toString(config.callbackMenuVersion()));
        return canonical + "." + encodedSignature(canonical, CALLBACK_SIGNATURE_BYTES);
    }

    public Optional<TelegramIdentityService.MutationCallback> consumeMutation(String token, long telegramUserId) {
        var identity = identities.findActiveByTelegramUserId(telegramUserId).orElse(null);
        if (identity == null || !"parent".equals(identity.getRole())) {
            return Optional.empty();
        }
        var callback = callbacks.findByDigest(digest(token)).orElse(null);
        if (callback == null || !identity.getId().equals(callback.getIdentityId())
            || !identity.getFamilyId().equals(callback.getFamilyId())) {
            return Optional.empty();
        }
        return identityService.consumeMutationCallback(token, Instant.ofEpochSecond(timeProvider.currentEpochSecond()));
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean matches(String canonical, String encodedSignature) {
        Optional<String> secret = config.callbackSigningSecret().filter(value -> !value.isBlank());
        if (secret.isEmpty()) {
            return false;
        }
        try {
            byte[] expected = Base64.getUrlDecoder().decode(signature(canonical));
            byte[] actual = Base64.getUrlDecoder().decode(encodedSignature);
            return MessageDigest.isEqual(expected, actual)
                || (actual.length == CALLBACK_SIGNATURE_BYTES || actual.length == 16)
                && MessageDigest.isEqual(Arrays.copyOf(expected, actual.length), actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String signature(String canonical) {
        String secret = config.callbackSigningSecret().filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalStateException("Telegram callback signing secret is unavailable"));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC SHA-256 is unavailable", exception);
        }
    }

    private String encodedSignature(String canonical, int bytes) {
        byte[] fullSignature = Base64.getUrlDecoder().decode(signature(canonical));
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(Arrays.copyOf(fullSignature, bytes));
    }

    public record VerifiedCallback(String action, long telegramUserId, Instant issuedAt) { }
}
