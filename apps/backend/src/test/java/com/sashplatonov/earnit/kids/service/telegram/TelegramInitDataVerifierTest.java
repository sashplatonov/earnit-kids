package com.sashplatonov.earnit.kids.service.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramInitDataVerifierTest {
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");

    @Test
    void verify_acceptsValidTelegramSignatureAndUser() throws Exception {
        TelegramConfig config = telegramConfig();
        TelegramInitDataVerifier verifier = new TelegramInitDataVerifier(
            config, new ObjectMapper(), TestConfigFactory.timeProvider(NOW));
        String raw = signedInitData(1786622100L, "{\"id\":77,\"first_name\":\"Kid\"}", "test-bot-token");

        assertThat(verifier.verify(raw)).contains(new TelegramInitDataVerifier.VerifiedInitData(77L, Instant.ofEpochSecond(1786622100L)));
    }

    @Test
    void verify_rejectsTamperedOrStaleData() throws Exception {
        TelegramConfig config = telegramConfig();
        TelegramInitDataVerifier verifier = new TelegramInitDataVerifier(
            config, new ObjectMapper(), TestConfigFactory.timeProvider(NOW));
        String raw = signedInitData(1786622100L, "{\"id\":77}", "test-bot-token");

        assertThat(verifier.verify(raw.replace("77", "78"))).isEmpty();
        assertThat(verifier.verify(signedInitData(1786610000L, "{\"id\":77}", "test-bot-token"))).isEmpty();
    }

    @Test
    void verify_rejectsDuplicateSignedFields() throws Exception {
        TelegramInitDataVerifier verifier = new TelegramInitDataVerifier(
            telegramConfig(), new ObjectMapper(), TestConfigFactory.timeProvider(NOW));
        String raw = signedInitData(1786622100L, "{\"id\":77}", "test-bot-token");

        assertThat(verifier.verify(raw + "&auth_date=1786622100")).isEmpty();
    }

    private TelegramConfig telegramConfig() {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.botToken()).thenReturn(Optional.of("test-bot-token"));
        when(config.initDataMaxAgeSeconds()).thenReturn(300);
        return config;
    }

    private String signedInitData(long authDate, String user, String botToken) throws Exception {
        String userValue = URLEncoder.encode(user, StandardCharsets.UTF_8);
        String withoutHash = "auth_date=" + authDate + "\nuser=" + user;
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
        String hash = HexFormat.of().formatHex(hmac(secret, withoutHash.getBytes(StandardCharsets.UTF_8)));
        return "auth_date=" + authDate + "&user=" + userValue + "&hash=" + hash;
    }

    private byte[] hmac(byte[] key, byte[] value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value);
    }
}
