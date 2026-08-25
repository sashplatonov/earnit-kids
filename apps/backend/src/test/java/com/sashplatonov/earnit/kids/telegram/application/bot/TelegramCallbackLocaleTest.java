package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.family.api.response.RequestDto;
import com.sashplatonov.earnit.kids.family.api.response.TaskDto;
import com.sashplatonov.earnit.kids.family.domain.model.FamilyLocale;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestType;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.telegram.application.callback.TelegramCallbackService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramCallbackLocaleTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void coinSuccessUsesEnglishViewForOutcomeAndFollowUpButtons() throws Exception {
        TelegramQuickActionResponse view = view(FamilyLocale.en);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        when(quickActions.load(7L, 1)).thenReturn(Optional.of(view));
        when(quickActions.adjustBalance(7L, 1, 2)).thenReturn(OperationResult.success(view));

        TelegramCoinAdjustmentHandler.handle(7L, "coins-apply-add-2-child-1", callback(),
            quickActions, apiClient, menuBuilder(), "https://mini.example.test");

        var edited = org.mockito.ArgumentCaptor.forClass(String.class);
        var buttons = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(apiClient).editMessageText(org.mockito.ArgumentMatchers.eq(44L),
            org.mockito.ArgumentMatchers.eq(91L), edited.capture(), buttons.capture());
        assertThat(edited.getValue()).contains("Added", "🟡 42 coins")
            .doesNotContain("Добавлено", "монет");
        assertThat(buttons.getValue()).extracting(button -> ((TelegramBotApiClient.InlineButton) button).text())
            .contains("🛠️ Other amount")
            .doesNotContain("🔢 Другая сумма");
    }

    @Test
    void coinRetryUsesRussianViewForOutcomeAndRetryButton() throws Exception {
        TelegramQuickActionResponse view = view(FamilyLocale.ru);
        TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
        TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
        when(quickActions.load(7L, 1)).thenReturn(Optional.of(view));
        when(quickActions.adjustBalance(7L, 1, -2))
            .thenReturn(OperationResult.failure("balance.failed", "failed"));

        TelegramCoinAdjustmentHandler.handle(7L, "coins-apply-remove-2-child-1", callback(),
            quickActions, apiClient, menuBuilder(), "https://mini.example.test");

        var edited = org.mockito.ArgumentCaptor.forClass(String.class);
        var buttons = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(apiClient).editMessageText(org.mockito.ArgumentMatchers.eq(44L),
            org.mockito.ArgumentMatchers.eq(91L), edited.capture(), buttons.capture());
        assertThat(edited.getValue()).contains("Не удалось выполнить действие", "Попробуйте ещё раз")
            .doesNotContain("Could not complete");
        assertThat(buttons.getValue()).extracting(button -> ((TelegramBotApiClient.InlineButton) button).text())
            .containsExactly("🔄 Повторить");
    }

    @Test
    void parentApprovalUsesLocaleAwareRequestFallback() throws Exception {
        for (FamilyLocale locale : List.of(FamilyLocale.en, FamilyLocale.ru)) {
            TelegramQuickActionResponse view = viewWithRequest(locale);
            TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
            TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
            when(quickActions.approveRequest(7L, 1, 19L)).thenReturn(OperationResult.success(view));

            TelegramParentRequestHandler.handle(7L, "parent.request.approve.1.19", callback(),
                quickActions, apiClient, menuBuilder());

            var edited = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(apiClient).editMessageText(org.mockito.ArgumentMatchers.eq(44L),
                org.mockito.ArgumentMatchers.eq(91L), edited.capture(), org.mockito.ArgumentMatchers.anyList());
            assertThat(edited.getValue()).contains(locale == FamilyLocale.en ? "Request" : "Запрос")
                .doesNotContain(locale == FamilyLocale.en ? "Запрос" : "Request");
        }
    }

    @Test
    void childTaskApprovalUsesLocaleAwareTaskFallback() throws Exception {
        for (FamilyLocale locale : List.of(FamilyLocale.en, FamilyLocale.ru)) {
            TelegramQuickActionResponse view = viewWithTask(locale);
            TelegramQuickActionService quickActions = mock(TelegramQuickActionService.class);
            TelegramBotApiClient apiClient = mock(TelegramBotApiClient.class);
            when(quickActions.load(7L, null)).thenReturn(Optional.of(view));
            when(quickActions.requestTask(7L, 1, 5L)).thenReturn(OperationResult.success(view));

            new TelegramChildActionHandler(quickActions, apiClient, menuBuilder()).task(7L, 5L, callback());

            var edited = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(apiClient).editMessageText(org.mockito.ArgumentMatchers.eq(44L),
                org.mockito.ArgumentMatchers.eq(91L), edited.capture(), org.mockito.ArgumentMatchers.anyList());
            assertThat(edited.getValue()).contains(locale == FamilyLocale.en ? "Task" : "Задание")
                .doesNotContain(locale == FamilyLocale.en ? "Задание" : "Task");
        }
    }

    private TelegramQuickActionResponse view(FamilyLocale locale) {
        return new TelegramQuickActionResponse("family", "parent", 1, "Alex", 42,
            List.of(), List.of(), List.of(), List.of(), List.of(), locale);
    }

    private TelegramQuickActionResponse viewWithRequest(FamilyLocale locale) {
        RequestDto request = new RequestDto(19L, null, null, null, null, null, null, null, null,
            null, 20, PurchaseRequestStatus.approved, PurchaseRequestType.earn, 0,
            "2026-08-13T12:00:00Z", 1, null, null, null, null);
        return new TelegramQuickActionResponse("family", "parent", 1, "Alex", 42,
            List.of(), List.of(), List.of(), List.of(request), List.of(), locale);
    }

    private TelegramQuickActionResponse viewWithTask(FamilyLocale locale) {
        TaskDto task = new TaskDto(5L, null, 2, null, null, null, null, null, null, null,
            true, 1, null, null, null);
        return new TelegramQuickActionResponse("family", "child", 1, "Alex", 42,
            List.of(), List.of(task), List.of(), List.of(), List.of(), locale);
    }

    private JsonNode callback() {
        try {
            return objectMapper.readTree("""
                {"message":{"chat":{"id":44},"message_id":91}}
                """);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private TelegramMenuBuilder menuBuilder() {
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        when(callbacks.signNavigation(anyString())).thenAnswer(invocation ->
            "nav." + invocation.getArgument(0, String.class) + ".signed");
        return new TelegramMenuBuilder(callbacks);
    }
}
