package com.sashplatonov.earnit.kids.telegram.application.bot;
import com.sashplatonov.earnit.kids.telegram.application.callback.TelegramCallbackService;

import com.sashplatonov.earnit.kids.family.api.response.ChildDto;
import com.sashplatonov.earnit.kids.family.api.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.family.api.response.RequestDto;
import com.sashplatonov.earnit.kids.family.api.response.ShopItemDto;
import com.sashplatonov.earnit.kids.family.api.response.TaskDto;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryType;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramMenuBuilderTest {
    @Test
    void parentChildPickerAddsPublicSiteUrlButtonWhenConfigured() {
        TelegramQuickActionResponse view = view();

        assertThat(menuBuilder().parentChildPicker(view, "https://example.test"))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("👧 Alex · 42", "🔗 Сайт");
        assertThat(menuBuilder().parentChildPicker(view, "https://example.test"))
            .filteredOn(button -> button.text().equals("🔗 Сайт"))
            .extracting(TelegramBotApiClient.InlineButton::url, TelegramBotApiClient.InlineButton::urlKind)
            .containsExactly(tuple("https://example.test", "url"));
    }

    @Test
    void parentChildPickerOmitsPublicSiteButtonWhenUrlBlank() {
        TelegramQuickActionResponse view = view();

        assertThat(menuBuilder().parentChildPicker(view, ""))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .doesNotContain("🔗 Сайт");
        assertThat(menuBuilder().parentChildPicker(view, "   "))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .doesNotContain("🔗 Сайт");
    }

    @Test
    void parentHomeShowsPendingAttentionCountInMessageBody() {
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 1, "Alex", 42,
            List.of(new ChildDto(1, "Alex", 42, 100, 0, "ocean", List.of(), List.of(),
                List.of(), List.of())),
            List.of(), List.of(),
            List.of(new RequestDto(19L, 7L, "Homework", null, null, "Homework", null, null,
                null, null, 20, PurchaseRequestStatus.pending, PurchaseRequestType.earn, 0,
                "2026-08-13T12:00:00Z", 1, null, null, null, null)),
            List.of());

        assertThat(TelegramMenuFlow.homeText(view))
            .isEqualTo("👧 Alex\n🟡 42 монеты\n\n🎯 Требуют внимания: 1");
    }

    @Test
    void childHomeTextUsesChildCopy() {
        assertThat(TelegramMenuFlow.homeText(new TelegramQuickActionResponse(
            "family", "child", 1, "Alex", 42, List.of(), List.of(), List.of(), List.of(), List.of())))
            .isEqualTo("👋 Alex\n🟡 42 монеты");
    }

    @Test
    void parentTaskNavigationDoesNotOpenLegacyMenu() {
        TelegramQuickActionResponse view = view();

        assertThat(TelegramMenuFlow.navigationMenu("tasks-child-1", view,
            "https://example.test/telegram", "", menuBuilder()))
            .isEmpty();
        assertThat(TelegramMenuFlow.navigationText("tasks-child-1", view))
            .isEqualTo("👧 Alex\n🟡 42 монеты\n\n✅ Сейчас ничего не требует внимания");
    }

    @Test
    void childCannotOpenParentCoinControlsFromSignedNavigation() {
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 1, "Alex", 42, List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(TelegramMenuFlow.navigationMenu("coins-child-1", view,
            "https://example.test/telegram", "", menuBuilder()))
            .isEmpty();
        assertThat(TelegramMenuFlow.navigationText("coins-child-1", view))
            .isEqualTo("👋 Alex\n🟡 42 монеты");
        assertThat(TelegramMenuFlow.navigationMenu("coins-confirm-remove-10-child-1", view,
            "https://example.test/telegram", "", menuBuilder()))
            .isEmpty();
        assertThat(TelegramMenuFlow.navigationText("coins-confirm-remove-10-child-1", view))
            .isEqualTo("👋 Alex\n🟡 42 монеты");
    }

    @Test
    void childTasksCapsRowsAndAddsMiniAppForMore() {
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 1, "Alex", 42, List.of(),
            java.util.stream.LongStream.range(1, 7)
                .mapToObj(id -> new com.sashplatonov.earnit.kids.family.api.response.TaskDto(
                    id, "Task " + id, 5, null, null, null, null, null, null, true, 1, null, null))
                .toList(), List.of(), List.of(), List.of());

        assertThat(menuBuilder().childTasks(view, "https://example.test/telegram"))
            .hasSize(6)
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("✅ Готово: Task 1", "✅ Готово: Task 2", "✅ Готово: Task 3",
                "✅ Готово: Task 4", "✅ Готово: Task 5", "📱 Все задания");
    }

    @Test
    void pendingTaskDoesNotExposeAnActiveDoneAction() {
        TaskDto available = new TaskDto(
            1L, "Утренний старт", 1, null, null, null, null, null, null, true, 1, null, null);
        TaskDto pendingTask = new TaskDto(
            2L, "Книжная искра", 2, null, null, null, null, null, null, true, 1, null, null);
        RequestDto pending = new RequestDto(19L, 2L, "Книжная искра", null, null, "Книжная искра",
            null, null, null, null, 2, PurchaseRequestStatus.pending, PurchaseRequestType.earn, 0,
            "2026-08-13T12:00:00Z", 1, null, null, null, null);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 1, "Alex", 42, List.of(), List.of(available, pendingTask),
            List.of(), List.of(pending), List.of());

        assertThat(menuBuilder().childTasks(view, "https://example.test/telegram"))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("✅ Готово: Утренний старт");
        assertThat(TelegramMenuFlow.navigationText("tasks-child-1", view))
            .isEqualTo("✅ Мои задания\n\n☀️ Утренний старт\n🟢 🟡 +1\n\n☀️ Книжная искра\n🟢 🟡 +2");
    }

    @Test
    void parentRequestQueueProcessesOnePendingRequestAtATime() {
        RequestDto request = new RequestDto(
            19L, 7L, "Homework", null, null, "Homework", null, null, null, null,
            20, PurchaseRequestStatus.pending, PurchaseRequestType.earn, 0, "2026-08-13T12:00:00Z",
            1, null, null, null, null);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 1, "Alex", 42, List.of(), List.of(), List.of(), List.of(request), List.of());

        assertThat(menuBuilder().parentRequestQueue(view, null))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("👍 Одобрить", "👎 Отклонить");
        assertThat(menuBuilder().parentRequestQueue(view, null))
            .extracting(TelegramBotApiClient.InlineButton::callbackData)
            .contains("parent.request.approve.1.19.queue", "parent.request.reject.1.19.queue");
        assertThat(TelegramMenuFlow.navigationText("requests-child-1", view))
            .isEqualTo("🎯 Запрос 1 из 1\n\n👧 Alex\n\nHomework\n🟢 🟡 +20 монет");
    }

    @Test
    void parentRequestQueueShowsNextWhenMorePendingExist() {
        RequestDto first = new RequestDto(
            19L, 7L, "Homework", null, null, "Homework", null, null, null, null,
            20, PurchaseRequestStatus.pending, PurchaseRequestType.earn, 0, "2026-08-13T12:00:00Z",
            1, null, null, null, null);
        RequestDto second = new RequestDto(
            20L, null, null, 9L, "Movie night", "Movie night", null, null, null, null,
            30, PurchaseRequestStatus.pending, PurchaseRequestType.shop_purchase, 0,
            "2026-08-13T13:00:00Z", 1, null, null, null, null);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 1, "Alex", 42, List.of(), List.of(), List.of(),
            List.of(first, second), List.of());

        assertThat(menuBuilder().parentRequestQueue(view, null))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("👍 Одобрить", "👎 Отклонить", "➡️ Следующий");
        assertThat(menuBuilder().parentRequestQueue(view, null))
            .extracting(TelegramBotApiClient.InlineButton::callbackData)
            .contains("nav.requests-next-19-child-1.signed");
        assertThat(TelegramMenuFlow.navigationText("requests-child-1", view))
            .isEqualTo("🎯 Запрос 1 из 2\n\n👧 Alex\n\nHomework\n🟢 🟡 +20 монет");
        assertThat(TelegramMenuFlow.navigationText("requests-next-19-child-1", view))
            .isEqualTo("🎯 Запрос 2 из 2\n\n👧 Alex\n\nMovie night\n🔴 🟡 -30 монет");
    }

    @Test
    void parentRequestsEmptyStateHasNoInlineActions() {
        TelegramQuickActionResponse view = view();

        assertThat(menuBuilder().parentRequestQueue(view, null)).isEmpty();
        assertThat(TelegramMenuFlow.navigationText("requests-child-1", view))
            .isEqualTo("✅ Нет запросов, ожидающих решения");
    }

    @Test
    void parentChildPickerShowsAuthorisedChildren() {
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 1, "Alex", 42,
            List.of(
                new ChildDto(1, "Alex", 42, 100, 0, "ocean", List.of(), List.of(), List.of(), List.of()),
                new ChildDto(2, "Sam", 18, 100, 0, "forest", List.of(), List.of(), List.of(), List.of())),
            List.of(), List.of(), List.of(), List.of());

        assertThat(menuBuilder().parentChildPicker(view, ""))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("👧 Alex · 42", "👧 Sam · 18");
    }

    @Test
    void parentCoinsIsAQuickActionWithProtectedHighRemovals() {
        TelegramQuickActionResponse view = view();

        List<TelegramBotApiClient.InlineButton> coins =
            menuBuilder().parentCoins(view, "https://example.test/telegram");
        assertThat(coins)
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("🟡 +1", "🟡 +2", "🟡 +5", "🟡 +10",
                "🟡 -1", "🟡 -2", "🟡 -5", "🟡 -10", "🔢 Другая сумма");
        assertThat(coins)
            .filteredOn(button -> button.text().equals("🔢 Другая сумма"))
            .extracting(TelegramBotApiClient.InlineButton::url)
            .containsExactly("https://example.test/telegram?context=coins");
        assertThat(menuBuilder().parentCoinConfirmation(view, -10))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("👍 Подтвердить");
        assertThat(TelegramMenuFlow.navigationText("coins-confirm-remove-10-child-1", view))
            .isEqualTo("Снять 10 монет с Alex?");
    }

    @Test
    void childRewardsShowsClaimButtonsAndGoalInBody() {
        ShopItemDto affordable = new ShopItemDto(1, "Королева настолки", 2, null, null, null, null, true, 1, null);
        ShopItemDto goal = new ShopItemDto(2, "Домашняя лаборатория", 30, null, null, null, null, true, 1, null);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 1, "Alex", 22, List.of(), List.of(), List.of(affordable, goal),
            List.of(), List.of());

        assertThat(menuBuilder().childRewards(view, "https://example.test/telegram"))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("🎁 Получить: Королева настолки");
        assertThat(TelegramMenuFlow.navigationText("rewards-child-1", view))
            .isEqualTo("🎁 Награды\n🟡 Баланс: 22\n\n🎁 Королева настолки\n🔴 🟡 -2\n\n"
                + "ℹ️ Следующая цель:\nДомашняя лаборатория · 30\nНе хватает 8 монет");
    }

    @Test
    void childRewardsEmptyStateWhenNothingAvailable() {
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 1, "Alex", 2, List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(menuBuilder().childRewards(view, "https://example.test/telegram"))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .isEmpty();
        assertThat(TelegramMenuFlow.navigationText("rewards-child-1", view))
            .isEqualTo("🎁 Сейчас нет доступных наград");
    }

    @Test
    void parentNoChildrenOffersOnlyMiniAppEntry() {
        assertThat(menuBuilder().parentNoChildren("https://example.test/telegram"))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("➕ Добавить ребёнка → Mini App");
    }

    @Test
    void recentIsAPreviewWithFullHistoryDeepLink() {
        TelegramQuickActionResponse empty = view();
        assertThat(menuBuilder().recent(empty, "https://example.test/telegram"))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("📱 Полная история");
        assertThat(TelegramRecent.format(empty, Instant.parse("2026-08-14T10:00:00Z")))
            .isEqualTo("📜 Последние события · Alex\n\n✅ Пока нет событий");
    }

    @Test
    void recentRowsArePresentationSafeAndRelative() {
        HistoryEntryDto earn = new HistoryEntryDto(1L, HistoryEntryType.earn, 1,
            "Утренний старт", null, 0, null, 7L, "Утренний старт", null, null, null, null,
            "2026-08-14T06:00:00Z", 1);
        HistoryEntryDto spend = new HistoryEntryDto(2L, HistoryEntryType.spend, -2,
            null, null, 0, null, null, null, 9L, "Королева настолки", null, null,
            "2026-08-12T20:15:00Z", 1);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 1, "Alex", 42, List.of(), List.of(), List.of(), List.of(),
            List.of(earn, spend));

        assertThat(TelegramRecent.format(view, Instant.parse("2026-08-14T10:00:00Z")))
            .isEqualTo("📜 Последние события · Alex\n\n"
                + "🟢 +1🟡 · Утренний старт\nСегодня, 06:00\n\n"
                + "🔴 -2🟡 · Королева настолки\n12 августа, 20:15");
    }

    private TelegramQuickActionResponse view() {
        return new TelegramQuickActionResponse(
            "family", "parent", 1, "Alex", 42,
            List.of(new ChildDto(1, "Alex", 42, 100, 0, "ocean", List.of(), List.of(), List.of(), List.of())),
            List.of(), List.of(), List.of(), List.of());
    }

    private TelegramMenuBuilder menuBuilder() {
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        when(callbacks.signNavigation(anyString())).thenAnswer(invocation ->
            "nav." + invocation.getArgument(0, String.class) + ".signed");
        return new TelegramMenuBuilder(callbacks);
    }
}
