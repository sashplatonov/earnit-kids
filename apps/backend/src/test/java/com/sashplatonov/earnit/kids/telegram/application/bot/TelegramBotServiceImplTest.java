package com.sashplatonov.earnit.kids.telegram.application.bot;
import com.sashplatonov.earnit.kids.telegram.application.callback.TelegramCallbackService;
import com.sashplatonov.earnit.kids.telegram.application.connection.TelegramFeatureGate;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramIdentityService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.family.api.response.ChildDto;
import com.sashplatonov.earnit.kids.family.api.response.RequestDto;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestType;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.membership.FamilyParentMembershipRepository;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramBotServiceImplTest {
    @Test
    void startSendsNoInlineKeyboardForUnlinkedUser() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramConfig config = mock(TelegramConfig.class);
        when(identities.recordWebhookUpdate(10L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        var service = service(identities, apiClient, callbacks, config);

        service.handleUpdate(new ObjectMapper().readTree(
            "{\"update_id\":10,\"message\":{\"chat\":{\"id\":44},\"text\":\"/start abc\"}}"));

        verify(apiClient).sendMessage(44L, "Open EarnIt Kids to continue.", List.of());
    }

    @Test
    void duplicateUpdateDoesNotCallTelegramApi() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramConfig config = mock(TelegramConfig.class);
        when(identities.recordWebhookUpdate(11L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(false);
        var service = service(identities, apiClient, callbacks, config);

        service.handleUpdate(new ObjectMapper().readTree(
            "{\"update_id\":11,\"message\":{\"chat\":{\"id\":44},\"text\":\"/start\"}}"));

        verify(apiClient, never()).sendMessage(any(Long.class), any(String.class), any());
    }

    @Test
    void malformedQuickActionStillAcknowledgesTheCallback() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramConfig config = mock(TelegramConfig.class);
        when(identities.recordWebhookUpdate(24L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        TelegramBotServiceImpl service = service(identities, apiClient, callbacks, config);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":24,"callback_query":{"id":"callback","from":{"id":77},
            "data":"task.request.invalid","message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(apiClient).answerCallbackQuery("callback");
    }

    @Test
    void callbackFailurePropagatesSoTelegramCanRetryTheWebhook() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramConfig config = mock(TelegramConfig.class);
        when(identities.recordWebhookUpdate(28L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(callbacks.verifyNavigation("nav.signed", 77L)).thenThrow(new IllegalStateException("transport down"));
        var service = service(identities, apiClient, callbacks, config);

        assertThatThrownBy(() -> service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":28,"callback_query":{"id":"callback","from":{"id":77},"data":"nav.signed",
            "message":{"chat":{"id":44},"message_id":19}}}
            """)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Telegram callback processing failed");

        verify(apiClient).answerCallbackQuery("callback");
    }

    @Test
    void stagedRolloutDoesNotProcessAnUnflaggedFamilyWebhookCallback() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramFeatureGate featureGate = mock(TelegramFeatureGate.class);
        FamilyRepository families = mock(FamilyRepository.class);
        when(identities.recordWebhookUpdate(25L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(
            new TelegramIdentityService.TelegramIdentity(1, 4, null, 77L, "parent")));
        when(families.findFamilyIdByDbId(4)).thenReturn(Optional.of("not-in-rollout"));
        when(featureGate.isBotEnabled("not-in-rollout")).thenReturn(false);
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            mock(TelegramQuickActionService.class), mock(TelegramMenuBuilder.class), featureGate, families);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":25,"callback_query":{"id":"callback","from":{"id":77},"data":"nav.signed",
            "message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(callbacks, never()).verifyNavigation("nav.signed", 77L);
        verify(apiClient).answerCallbackQuery("callback");
    }

    @Test
    void unlinkedStartSendsNoInlineKeyboard() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramFeatureGate featureGate = mock(TelegramFeatureGate.class);
        FamilyRepository families = mock(FamilyRepository.class);
        when(identities.recordWebhookUpdate(26L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.empty());
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            null, null, featureGate, families);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":26,"message":{"chat":{"id":44},"from":{"id":77},"text":"/start"}}
            """));

        verify(apiClient).sendMessage(44L, "Open EarnIt Kids to continue.", List.of());
    }

    @Test
    void linkedStartSendsMenuWhenTheFamilyIsInTheBotRollout() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramFeatureGate featureGate = mock(TelegramFeatureGate.class);
        FamilyRepository families = mock(FamilyRepository.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 3, "Alex", 20, List.of(), List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(27L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(
            new TelegramIdentityService.TelegramIdentity(1, 4, 3, 77L, "child")));
        when(families.findFamilyIdByDbId(4)).thenReturn(Optional.of("family"));
        when(featureGate.isBotEnabled("family")).thenReturn(true);
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder, featureGate, families);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":27,"message":{"chat":{"id":44},"from":{"id":77},"text":"/start"}}
            """));

        verify(apiClient).sendMessageWithReplyKeyboard(
            eq(44L),
            eq("👋 Alex\n🟡 20 монет"),
            argThat((TelegramReplyKeyboard kb) ->
                kb.rows().size() == 2
                && kb.rows().get(0).buttons().get(0).label().equals(TelegramCopy.myTasks(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru))
                && kb.rows().get(0).buttons().get(1).label().equals(TelegramCopy.rewards(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru))
                && kb.rows().get(1).buttons().size() == 1
                && kb.rows().get(1).buttons().get(0).label().equals(TelegramCopy.recent(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru))
            )
        );
    }

    @Test
    void startFailurePropagatesSoTelegramCanRetryTheWebhook() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramConfig config = mock(TelegramConfig.class);
        when(identities.recordWebhookUpdate(12L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        org.mockito.Mockito.doThrow(new IllegalStateException("transport down"))
            .when(apiClient).sendMessage(any(Long.class), any(String.class), any());
        var service = service(identities, apiClient, callbacks, config);

        assertThatThrownBy(() -> service.handleUpdate(new ObjectMapper().readTree(
            "{\"update_id\":12,\"message\":{\"chat\":{\"id\":44},\"text\":\"/start\"}}")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Telegram message processing failed");
    }

    @Test
    void signedNavigationEditsTheOriginatingMenu() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 3, "Alex", 20, List.of(), List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(13L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        when(callbacks.verifyNavigation("nav.signed", 77L)).thenReturn(Optional.of(
            new TelegramCallbackService.VerifiedCallback("tasks", 77L, Instant.parse("2026-08-13T12:00:00Z"))));
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        when(menuBuilder.childTasks(view, "https://example.test/telegram")).thenReturn(List.of());
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":13,"callback_query":{"id":"callback","from":{"id":77},"data":"nav.signed",
            "message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(apiClient).editMessageText(44L, 19L, "✅ На сегодня активных заданий нет", List.of());
        verify(apiClient).answerCallbackQuery("callback");
    }

    @Test
    void parentStartShowsParentHomeDecisionMenu() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 1, "Alex", 20,
            List.of(
                new ChildDto(1, "Alex", 20, 100, 0, "ocean", List.of(), List.of(), List.of(), List.of()),
                new ChildDto(2, "Sam", 12, 100, 0, "forest", List.of(), List.of(), List.of(), List.of())),
            List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(16L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":16,"message":{"chat":{"id":44},"from":{"id":77},"text":"/start"}}
            """));

        verify(apiClient).sendMessageWithReplyKeyboard(
            eq(44L),
            eq("👧 Alex\n🟡 20 монет\n\n✅ Сейчас ничего не требует внимания"),
            argThat((TelegramReplyKeyboard kb) ->
                kb.rows().size() == 2
                && kb.rows().get(0).buttons().get(0).label().equals(TelegramCopy.requests(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru))
                && kb.rows().get(0).buttons().get(1).label().equals(TelegramCopy.coins(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru))
                && kb.rows().get(1).buttons().get(0).label().equals(TelegramCopy.recent(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru))
                && kb.rows().get(1).buttons().size() == 2
                && kb.rows().get(1).buttons().get(1).label().equals(TelegramCopy.settings(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru))
            )
        );
    }

    @Test
    void parentStartOmitsLanguageForEditorMembership() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        FamilyRepository families = mock(FamilyRepository.class);
        FamilyParentMembershipRepository memberships = mock(FamilyParentMembershipRepository.class);
        FamilyParentMembershipEntity membership = mock(FamilyParentMembershipEntity.class);
        when(identities.recordWebhookUpdate(17L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(
            new TelegramIdentityService.TelegramIdentity(1, 2, null, 77L, "parent", 9)));
        when(memberships.findByParentAndFamily(9, 2)).thenReturn(Optional.of(membership));
        when(membership.getPermission()).thenReturn(FamilyParentMembershipEntity.Permission.editor);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(new TelegramQuickActionResponse(
            "family", "parent", 1, "Alex", 20, List.of(), List.of(), List.of(), List.of(), List.of())));
        when(config.publicSiteUrl()).thenReturn(Optional.empty());
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(identities, apiClient, callbacks, config,
            () -> Instant.parse("2026-08-13T12:00:00Z"), quickActions, menuBuilder, null, families, memberships);

        service.handleUpdate(new ObjectMapper().readTree(
            "{\"update_id\":17,\"message\":{\"chat\":{\"id\":44},\"from\":{\"id\":77},\"text\":\"/start\"}}"));

        verify(apiClient).sendMessageWithReplyKeyboard(eq(44L), org.mockito.ArgumentMatchers.anyString(),
            argThat(keyboard -> keyboard.rows().size() == 2));
    }

    @Test
    void parentStartWithNoChildrenOffersAddChildMiniApp() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        when(identities.recordWebhookUpdate(19L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(
            new TelegramIdentityService.TelegramIdentity(1, 2, null, 77L, "parent")));
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        when(quickActions.load(77L, null)).thenReturn(Optional.empty());
        List<TelegramBotApiClient.InlineButton> addChild = List.of(
            TelegramBotApiClient.InlineButton.webApp("Add child → Mini App", "https://example.test/telegram"));
        when(menuBuilder.parentNoChildren("https://example.test/telegram")).thenReturn(addChild);
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":19,"message":{"chat":{"id":44},"from":{"id":77},"text":"/start"}}
            """));

        verify(apiClient).sendMessage(44L, "Детей пока нет", addChild);
    }

    @Test
    void childSelectionReloadsTheSelectedChildBeforeEditingMenu() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 2, "Sam", 12, List.of(), List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(17L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(callbacks.verifyNavigation("nav.signed", 77L)).thenReturn(Optional.of(
            new TelegramCallbackService.VerifiedCallback("child-2", 77L,
                Instant.parse("2026-08-13T12:00:00Z"))));
        when(quickActions.load(77L, 2)).thenReturn(Optional.of(view));
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":17,"callback_query":{"id":"callback","from":{"id":77},"data":"nav.signed",
            "message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(quickActions).load(77L, 2);
        verify(apiClient).editMessageText(44L, 19L,
            "👧 Sam\n🟡 12 монет\n\n✅ Сейчас ничего не требует внимания", List.of());
    }

    @Test
    void parentNavigationKeepsTheCallbackChildScope() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 2, "Sam", 12, List.of(), List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(23L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(callbacks.verifyNavigation("nav.signed", 77L)).thenReturn(Optional.of(
            new TelegramCallbackService.VerifiedCallback("requests-child-2", 77L,
                Instant.parse("2026-08-13T12:00:00Z"))));
        when(quickActions.load(77L, 2)).thenReturn(Optional.of(view));
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":23,"callback_query":{"id":"callback","from":{"id":77},"data":"nav.signed",
            "message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(quickActions).load(77L, 2);
        verify(apiClient).editMessageText(44L, 19L,
            "✅ Нет запросов, ожидающих решения", List.of());
    }

    @Test
    void confirmedCoinAdjustmentUsesSelectedChildAndDelta() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 2, "Sam", 2, List.of(), List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(18L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(callbacks.verifyNavigation("nav.signed", 77L)).thenReturn(Optional.of(
            new TelegramCallbackService.VerifiedCallback("coins-apply-remove-10-child-2", 77L,
                Instant.parse("2026-08-13T12:00:00Z"))));
        when(quickActions.adjustBalance(77L, 2, -10)).thenReturn(OperationResult.success(view));
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        when(menuBuilder.parentCoins(view, "https://example.test/telegram")).thenReturn(List.of());
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":18,"callback_query":{"id":"callback","from":{"id":77},"data":"nav.signed",
            "message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(quickActions).adjustBalance(77L, 2, -10);
        verify(apiClient).editMessageText(44L, 19L, "✅ Снято 🔴 🟡 -10 монет\n🟡 2 монеты", List.of());
    }

    @Test
    void coinConfirmationNamesOperationAndSelectedChild() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 2, "Sam", 12, List.of(), List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(20L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(callbacks.verifyNavigation("nav.signed", 77L)).thenReturn(Optional.of(
            new TelegramCallbackService.VerifiedCallback("coins-confirm-remove-10-child-2", 77L,
                Instant.parse("2026-08-13T12:00:00Z"))));
        when(quickActions.load(77L, 2)).thenReturn(Optional.of(view));
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        when(menuBuilder.parentCoinConfirmation(view, -10)).thenReturn(List.of());
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":20,"callback_query":{"id":"callback","from":{"id":77},"data":"nav.signed",
            "message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(apiClient).editMessageText(44L, 19L, "Снять 10 монет с Sam?", List.of());
    }

    @Test
    void taskRequestEditsTheCardToWaitingState() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 3, "Alex", 20, List.of(),
            List.of(new com.sashplatonov.earnit.kids.family.api.response.TaskDto(
                3_000_000_000L, "Утренний старт", 1, null, null, null, null, null, null, null, true, 3, null, null, null)),
            List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(14L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        when(quickActions.requestTask(77L, 3, 3_000_000_000L)).thenReturn(OperationResult.success(view));
        when(menuBuilder.backToMain()).thenReturn(List.of());
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":14,"callback_query":{"id":"callback","from":{"id":77},
            "data":"task.request.3000000000","message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(apiClient).editMessageText(44L, 19L, "⏳ Утренний старт\nЖдём решения родителя", List.of());
        verify(apiClient).answerCallbackQuery("callback");
    }

    @Test
    void failedTaskRequestShowsErrorWithRetryAndHome() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 3, "Alex", 20, List.of(), List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(40L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        when(quickActions.requestTask(77L, 3, 5L)).thenReturn(OperationResult.failure("LIMIT", "reached"));
        List<TelegramBotApiClient.InlineButton> retry = List.of(
            TelegramBotApiClient.InlineButton.callback("🔄 Повторить", "task.request.5"));
        when(menuBuilder.childRetry("task.request.5")).thenReturn(retry);
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":40,"callback_query":{"id":"callback","from":{"id":77},
            "data":"task.request.5","message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(apiClient).editMessageText(44L, 19L,
            "⚠️ Не удалось выполнить действие\nПопробуйте ещё раз", retry);
    }

    @Test
    void rewardRequestEditsTheCardToWaitingState() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 3, "Alex", 20, List.of(), List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(22L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        when(quickActions.requestReward(77L, 3, 8_000_000_000L)).thenReturn(OperationResult.success(view));
        when(menuBuilder.backToMain()).thenReturn(List.of());
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":22,"callback_query":{"id":"callback","from":{"id":77},
            "data":"reward.request.8000000000","message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(quickActions).requestReward(77L, 3, 8_000_000_000L);
        verify(apiClient).editMessageText(44L, 19L, "⏳ Заявка отправлена родителю", List.of());
    }

    @Test
    void parentApprovalEditsNotificationToTerminalResolvedState() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        RequestDto request = new RequestDto(19L, 7L, "Homework", null, null, "Homework", null, null, null, null,
            1, PurchaseRequestStatus.approved, PurchaseRequestType.earn, 0, "2026-08-13T12:00:00Z",
            3, null, null, null, null);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 3, "Alex", 62, List.of(), List.of(), List.of(), List.of(request), List.of());
        when(identities.recordWebhookUpdate(15L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(quickActions.approveRequest(77L, 3, 19L)).thenReturn(OperationResult.success(view));
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":15,"callback_query":{"id":"callback","from":{"id":77},
            "data":"parent.request.approve.3.19","message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(apiClient).editMessageText(44L, 19L,
            "✅ Одобрено\n\nHomework\n🟢 🟡 +1 монета\nБаланс: 62", List.of());
        verify(apiClient).answerCallbackQuery("callback");
    }

    @Test
    void queueDecisionAutoAdvancesToTheNextPendingRequest() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        RequestDto pending = new RequestDto(20L, null, null, 9L, "Movie night", "Movie night",
            null, null, null, null, 30, PurchaseRequestStatus.pending, PurchaseRequestType.shop_purchase, 0,
            "2026-08-13T13:00:00Z", 3, null, null, null, null);
        RequestDto resolved = new RequestDto(19L, 7L, "Homework", null, null, "Homework",
            null, null, null, null, 20, PurchaseRequestStatus.approved, PurchaseRequestType.earn, 0,
            "2026-08-13T12:00:00Z", 3, null, null, null, null);
        TelegramQuickActionResponse after = new TelegramQuickActionResponse(
            "family", "parent", 3, "Alex", 62, List.of(), List.of(), List.of(),
            List.of(pending, resolved), List.of());
        when(identities.recordWebhookUpdate(30L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(quickActions.approveRequest(77L, 3, 19L)).thenReturn(OperationResult.success(after));
        List<TelegramBotApiClient.InlineButton> next = List.of(
            TelegramBotApiClient.InlineButton.callback("👍 Одобрить", "nav.signed"));
        when(menuBuilder.parentRequestQueue(after, null)).thenReturn(next);
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":30,"callback_query":{"id":"callback","from":{"id":77},
            "data":"parent.request.approve.3.19.queue","message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(apiClient).editMessageText(44L, 19L,
            "🎯 Запрос 1 из 1\n\n👧 Alex\n\nMovie night\n🔴 🟡 -30 монет", next);
    }

    @Test
    void lastQueueDecisionRendersCompletionState() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramQuickActionResponse after = new TelegramQuickActionResponse(
            "family", "parent", 3, "Alex", 62, List.of(), List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(31L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        when(quickActions.rejectRequest(77L, 3, 19L)).thenReturn(OperationResult.success(after));
        when(menuBuilder.parentRequestQueue(after, null)).thenReturn(List.of());
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":31,"callback_query":{"id":"callback","from":{"id":77},
            "data":"parent.request.reject.3.19.queue","message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(apiClient).editMessageText(44L, 19L,
            "✅ Нет запросов, ожидающих решения", List.of());
    }

    @Test
    void staleParentApprovalShowsStaleStateWithoutDecisionButtons() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        RequestDto request = new RequestDto(19L, 7L, "Homework", null, null, "Homework", null, null, null, null,
            20, PurchaseRequestStatus.approved, PurchaseRequestType.earn, 0, "2026-08-13T12:00:00Z",
            3, null, null, null, null);
        TelegramQuickActionResponse refreshed = new TelegramQuickActionResponse(
            "family", "parent", 3, "Alex", 62, List.of(), List.of(), List.of(), List.of(request), List.of());
        when(identities.recordWebhookUpdate(21L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(quickActions.approveRequest(77L, 3, 19L)).thenReturn(OperationResult.failure("already", "processed"));
        when(quickActions.load(77L, 3)).thenReturn(Optional.of(refreshed));
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":21,"callback_query":{"id":"callback","from":{"id":77},
            "data":"parent.request.approve.3.19","message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(apiClient).editMessageText(44L, 19L, "ℹ️ Этот запрос уже обработан", List.of());
    }

    @Test
    void childPickerNormalizesPublicSiteUrlToTheSiteRoot() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 2, "Sam", 12, List.of(), List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(41L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(callbacks.verifyNavigation("nav.signed", 77L)).thenReturn(Optional.of(
            new TelegramCallbackService.VerifiedCallback("switch-child-2", 77L,
                Instant.parse("2026-08-13T12:00:00Z"))));
        when(quickActions.load(77L, 2)).thenReturn(Optional.of(view));
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        // EXPLAIN: APP_URL may carry a path/query; the public site button must
        // EXPLAIN: still point at the bare origin.
        when(config.publicSiteUrl()).thenReturn(Optional.of("https://example.test/en/app/tasks?tab=1"));
        when(menuBuilder.parentChildPicker(view, "https://example.test")).thenReturn(List.of());
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":41,"callback_query":{"id":"callback","from":{"id":77},"data":"nav.signed",
            "message":{"chat":{"id":44},"message_id":19}}}
            """));

        verify(menuBuilder).parentChildPicker(view, "https://example.test");
        verify(apiClient).editMessageText(44L, 19L, "👧 Кого показывать?", List.of());
    }

    @Test
    void siteUrlButtonSendsInlineUrlButton() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramConfig config = mock(TelegramConfig.class);
        when(identities.recordWebhookUpdate(50L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(config.publicSiteUrl()).thenReturn(Optional.of("https://example.test/en/app"));
        var service = service(identities, apiClient, callbacks, config);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":50,"message":{"chat":{"id":44},"from":{"id":77},"text":"🔗 Сайт"}}
            """));

        // EXPLAIN: 🔗 Сайт is a plain text button (KeyboardButton has no `url`
        // EXPLAIN: field), so the bot answers with one inline URL button using
        // EXPLAIN: the same heading as the tapped button.
        verify(apiClient).sendMessage(eq(44L), eq(TelegramCopy.site(com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale.ru)), any());
        verify(apiClient, never()).sendMessageWithReplyKeyboard(any(Long.class), any(String.class), any());
    }

    @Test
    void startResetsStaleReplyKeyboardOncePerVersion() throws Exception {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramConfig config = mock(TelegramConfig.class);
        TelegramFeatureGate featureGate = mock(TelegramFeatureGate.class);
        FamilyRepository families = mock(FamilyRepository.class);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 3, "Alex", 20, List.of(), List.of(), List.of(), List.of(), List.of());
        when(identities.recordWebhookUpdate(60L, Instant.parse("2026-08-13T12:00:00Z"))).thenReturn(true);
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(
            new TelegramIdentityService.TelegramIdentity(1, 4, 3, 77L, "child")));
        when(families.findFamilyIdByDbId(4)).thenReturn(Optional.of("family"));
        when(featureGate.isBotEnabled("family")).thenReturn(true);
        when(config.miniAppUrl()).thenReturn(Optional.of("https://example.test/telegram"));
        when(config.replyKeyboardVersion()).thenReturn(2);
        when(identities.needsReplyKeyboardReset(77L, 2)).thenReturn(true);
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        TelegramBotServiceImpl service = new TelegramBotServiceImpl(
            identities, apiClient, callbacks, config, () -> Instant.parse("2026-08-13T12:00:00Z"),
            quickActions, menuBuilder, featureGate, families);

        service.handleUpdate(new ObjectMapper().readTree("""
            {"update_id":60,"message":{"chat":{"id":44},"from":{"id":77},"text":"/start"}}
            """));

        verify(apiClient).removeReplyKeyboard(44L);
        verify(identities).markReplyKeyboardVersion(77L, 2);
        verify(apiClient).sendMessageWithReplyKeyboard(eq(44L), any(String.class), any());
    }

    private TelegramBotServiceImpl service(TelegramIdentityService identities,
                                           TelegramBotApiClient apiClient,
                                           TelegramCallbackService callbacks,
                                           TelegramConfig config) {
        return new TelegramBotServiceImpl(identities, apiClient, callbacks, config,
            () -> Instant.parse("2026-08-13T12:00:00Z"));
    }
}
