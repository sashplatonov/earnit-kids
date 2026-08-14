package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.TelegramIdentityEntity;
import com.sashplatonov.earnit.kids.repository.TelegramIdentityRepository;
import com.sashplatonov.earnit.kids.repository.TelegramCallbackActionRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramCallbackServiceTest {
    @Test
    void signedNavigationRoundTripsForCurrentMenuVersion() {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.callbackSigningSecret()).thenReturn(Optional.of("callback-secret"));
        when(config.callbackMenuVersion()).thenReturn(1);
        when(config.callbackTtlSeconds()).thenReturn(300);
        TimeProvider time = () -> Instant.ofEpochSecond(1_000L);
        TelegramCallbackService service = new TelegramCallbackService(config, mock(TelegramIdentityRepository.class),
            mock(TelegramCallbackActionRepository.class), time, mock(TelegramIdentityService.class));

        String data = service.signNavigation("tasks");

        assertThat(service.verifyNavigation(data, 42L)).contains(
            new TelegramCallbackService.VerifiedCallback("tasks", 42L, Instant.ofEpochSecond(1_000L)));
    }

    @Test
    void signedNavigationFitsTelegramCallbackDataLimit() {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.callbackSigningSecret()).thenReturn(Optional.of("callback-secret"));
        when(config.callbackMenuVersion()).thenReturn(1);
        when(config.callbackTtlSeconds()).thenReturn(300);
        TelegramCallbackService service = new TelegramCallbackService(config,
            mock(TelegramIdentityRepository.class), mock(TelegramCallbackActionRepository.class),
            () -> Instant.ofEpochSecond(1_000L), mock(TelegramIdentityService.class));

        String data = service.signNavigation("tasks-child-1234567890");

        assertThat(data.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(64);
        assertThat(service.verifyNavigation(data, 42L)).isPresent();

        String coinData = service.signNavigation("coins-confirm-remove-10-child-1234567890");
        assertThat(coinData.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(64);
        assertThat(service.verifyNavigation(coinData, 42L)).contains(
            new TelegramCallbackService.VerifiedCallback(
                "coins-confirm-remove-10-child-1234567890", 42L, Instant.ofEpochSecond(1_000L)));

        String childData = service.signNavigation("tasks-child-42");
        assertThat(service.verifyNavigation(childData, 42L)).contains(
            new TelegramCallbackService.VerifiedCallback("tasks-child-42", 42L,
                Instant.ofEpochSecond(1_000L)));
    }
    @Test
    void verifiesSignedVersionedNavigationCallback() throws Exception {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.callbackSigningSecret()).thenReturn(Optional.of("callback-secret"));
        when(config.callbackMenuVersion()).thenReturn(1);
        when(config.callbackTtlSeconds()).thenReturn(300);
        TelegramCallbackService service = new TelegramCallbackService(config,
            mock(TelegramIdentityRepository.class), mock(TelegramCallbackActionRepository.class),
            () -> Instant.ofEpochSecond(1000),
            mock(TelegramIdentityService.class));
        String canonical = "nav.recent.900.1";
        String callback = canonical + "." + signature(canonical, "callback-secret");

        assertThat(service.verifyNavigation(callback, 77L)).isPresent();
        assertThat(service.verifyNavigation(canonical + ".bad", 77L)).isEmpty();
    }

    @Test
    void rejectsExpiredNavigationCallback() throws Exception {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.callbackSigningSecret()).thenReturn(Optional.of("callback-secret"));
        when(config.callbackMenuVersion()).thenReturn(1);
        when(config.callbackTtlSeconds()).thenReturn(300);
        TelegramCallbackService service = new TelegramCallbackService(config,
            mock(TelegramIdentityRepository.class), mock(TelegramCallbackActionRepository.class),
            () -> Instant.ofEpochSecond(1000),
            mock(TelegramIdentityService.class));
        String canonical = "nav.recent.600.1";

        assertThat(service.verifyNavigation(canonical + "." + signature(canonical, "callback-secret"), 77L)).isEmpty();
    }

    @Test
    void consumesMutationOnlyForTheReferencedActiveParent() {
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramIdentityRepository identities = mock(TelegramIdentityRepository.class);
        TelegramCallbackActionRepository callbacks = mock(TelegramCallbackActionRepository.class);
        TelegramIdentityService identityService = mock(TelegramIdentityService.class);
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(
            TelegramIdentityEntity.builder().id(5).familyId(2).telegramUserId(77L).role("parent").active(true).build()));
        when(callbacks.findByDigest(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.of(
            com.sashplatonov.earnit.kids.domain.model.TelegramCallbackActionEntity.builder()
                .identityId(5).familyId(2).build()));
        when(identityService.consumeMutationCallback("token", Instant.ofEpochSecond(1000)))
            .thenReturn(Optional.of(new TelegramIdentityService.MutationCallback(9, 2, 5, "APPROVE", 10)));

        var result = new TelegramCallbackService(config, identities, callbacks,
            () -> Instant.ofEpochSecond(1000), identityService)
            .consumeMutation("token", 77L);

        assertThat(result).isPresent();
    }

    private String signature(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
