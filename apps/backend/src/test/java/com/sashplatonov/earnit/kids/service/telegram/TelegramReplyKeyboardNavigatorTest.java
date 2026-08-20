package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.config.TelegramConfig;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramReplyKeyboardNavigatorTest {
    @Test
    void parentRequestsNavigationSendsDecisionButtons() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramConfig config = config();
        TelegramQuickActionResponse view = parentView();
        List<TelegramBotApiClient.InlineButton> buttons = List.of(
            TelegramBotApiClient.InlineButton.callback(TelegramCopy.APPROVE, "approve"),
            TelegramBotApiClient.InlineButton.callback(TelegramCopy.REJECT, "reject"));
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        when(menuBuilder.parentRequestQueue(view, null)).thenReturn(buttons);

        navigator(quickActions, menuBuilder, config, apiClient).handle(
            message(TelegramCopy.NAV_REQUESTS), 44L, 77L);

        verify(apiClient).sendMessage(eq(44L), eq(TelegramCopy.requestQueueText(
            "Alex", "Morning task", 2, true, 1, 1)), eq(buttons));
    }

    @Test
    void parentCoinsNavigationSendsCoinButtons() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramConfig config = config();
        TelegramQuickActionResponse view = parentView();
        List<TelegramBotApiClient.InlineButton> buttons = List.of(
            TelegramBotApiClient.InlineButton.callback(TelegramCopy.coinAdd(1), "add"));
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        when(menuBuilder.parentCoins(view, "https://mini.example.test")).thenReturn(buttons);

        navigator(quickActions, menuBuilder, config, apiClient).handle(
            message(TelegramCopy.NAV_COINS), 44L, 77L);

        verify(apiClient).sendMessage(eq(44L), eq(TelegramCopy.parentCoins("Alex", 20)), eq(buttons));
    }

    @Test
    void parentSelectChildNavigationSendsChildPickerButtons() throws Exception {
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramMenuBuilder menuBuilder = mock(TelegramMenuBuilder.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        TelegramConfig config = config();
        TelegramQuickActionResponse view = parentView();
        List<TelegramBotApiClient.InlineButton> buttons = List.of(
            TelegramBotApiClient.InlineButton.callback("👧 Alex · 20", "child"));
        when(quickActions.load(77L, null)).thenReturn(Optional.of(view));
        when(menuBuilder.parentChildPicker(view, "https://site.example.test")).thenReturn(buttons);

        navigator(quickActions, menuBuilder, config, apiClient).handle(
            message(TelegramCopy.NAV_SELECT_CHILD), 44L, 77L);

        verify(apiClient).sendMessage(eq(44L), eq(TelegramCopy.chooseChildTitle()), eq(buttons));
    }

    private static TelegramReplyKeyboardNavigator navigator(TelegramQuickActionService quickActions,
                                                              TelegramMenuBuilder menuBuilder,
                                                              TelegramConfig config,
                                                              TelegramBotApiClient apiClient) {
        return new TelegramReplyKeyboardNavigator(quickActions, menuBuilder, config, apiClient);
    }

    private static TelegramConfig config() {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.miniAppUrl()).thenReturn(Optional.of("https://mini.example.test"));
        when(config.publicSiteUrl()).thenReturn(Optional.of("https://site.example.test"));
        return config;
    }

    private static TelegramQuickActionResponse parentView() {
        RequestDto request = new RequestDto(19L, 2L, "Morning task", null, null, null,
            null, null, null, null, 2, PurchaseRequestStatus.pending, PurchaseRequestType.earn,
            0, "2026-08-20T19:51:00Z", 1, null, null, null, null);
        return new TelegramQuickActionResponse("family", "parent", 1, "Alex", 20,
            List.of(), List.of(), List.of(), List.of(request), List.of());
    }

    private static JsonNode message(String text) {
        return new ObjectMapper().createObjectNode().put("text", text);
    }
}
