package com.sashplatonov.earnit.kids.admin.application;

import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AdminAccessServiceTest {

    @Test
    void testNoAdminsConfigured() {
        TelegramConfig config = createConfig(Optional.empty());
        AdminAccessService service = new AdminAccessService(config);

        assertFalse(service.hasAdmins());
        assertEquals(0, service.getAdminCount());
        assertFalse(service.isAdmin(123456789L));
    }

    @Test
    void testBlankAdminUserIds() {
        TelegramConfig config = createConfig(Optional.of(""));
        AdminAccessService service = new AdminAccessService(config);

        assertFalse(service.hasAdmins());
        assertEquals(0, service.getAdminCount());
    }

    @Test
    void testSingleAdminUserId() {
        TelegramConfig config = createConfig(Optional.of("123456789"));
        AdminAccessService service = new AdminAccessService(config);

        assertTrue(service.hasAdmins());
        assertEquals(1, service.getAdminCount());
        assertTrue(service.isAdmin(123456789L));
        assertFalse(service.isAdmin(987654321L));
        assertFalse(service.isAdmin(null));
    }

    @Test
    void testMultipleAdminUserIds() {
        TelegramConfig config = createConfig(Optional.of("123456789,987654321,555666777"));
        AdminAccessService service = new AdminAccessService(config);

        assertTrue(service.hasAdmins());
        assertEquals(3, service.getAdminCount());
        assertTrue(service.isAdmin(123456789L));
        assertTrue(service.isAdmin(987654321L));
        assertTrue(service.isAdmin(555666777L));
        assertFalse(service.isAdmin(111222333L));
    }

    @Test
    void testAdminUserIdsWithSpaces() {
        TelegramConfig config = createConfig(Optional.of("123456789, 987654321 , 555666777"));
        AdminAccessService service = new AdminAccessService(config);

        assertEquals(3, service.getAdminCount());
        assertTrue(service.isAdmin(123456789L));
        assertTrue(service.isAdmin(987654321L));
        assertTrue(service.isAdmin(555666777L));
    }

    @Test
    void testInvalidAdminUserIdsAreSkipped() {
        TelegramConfig config = createConfig(Optional.of("123456789,invalid,987654321,abc"));
        AdminAccessService service = new AdminAccessService(config);

        assertEquals(2, service.getAdminCount());
        assertTrue(service.isAdmin(123456789L));
        assertTrue(service.isAdmin(987654321L));
    }

    @Test
    void testNullTelegramUserId() {
        TelegramConfig config = createConfig(Optional.of("123456789"));
        AdminAccessService service = new AdminAccessService(config);

        assertFalse(service.isAdmin(null));
    }

    private TelegramConfig createConfig(Optional<String> adminUserIds) {
        return new TelegramConfig() {
            @Override public Optional<String> botToken() { return Optional.empty(); }
            @Override public Optional<String> botUsername() { return Optional.empty(); }
            @Override public Optional<String> miniAppUrl() { return Optional.empty(); }
            @Override public Optional<String> publicSiteUrl() { return Optional.empty(); }
            @Override public int initDataMaxAgeSeconds() { return 300; }
            @Override public boolean enabled() { return false; }
            @Override public boolean miniAppEnabled() { return false; }
            @Override public boolean botEnabled() { return false; }
            @Override public boolean notificationsEnabled() { return false; }
            @Override public Optional<String> adminUserIds() { return adminUserIds; }
            @Override public Optional<String> rolloutFamilyId() { return Optional.empty(); }
            @Override public boolean outboxEnabled() { return false; }
            @Override public int outboxMaxAttempts() { return 5; }
            @Override public Optional<String> webhookSecret() { return Optional.empty(); }
            @Override public Optional<String> callbackSigningSecret() { return Optional.empty(); }
            @Override public int callbackTtlSeconds() { return 300; }
            @Override public int callbackMenuVersion() { return 1; }
            @Override public int replyKeyboardVersion() { return 1; }
        };
    }
}
