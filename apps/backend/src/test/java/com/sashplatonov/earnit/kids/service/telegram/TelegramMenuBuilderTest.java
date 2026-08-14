package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.ChildDto;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramMenuBuilderTest {
    @Test
    void parentMainHasFiveBoundedActions() {
        TelegramQuickActionResponse view = view();

        assertThat(menuBuilder().parentMain(view, "https://example.test/telegram"))
            .hasSize(5)
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("🎯 Requests (0)", "🪙 Coins", "📜 Recent", "🔄 Switch child", "📱 Open Mini App");
        assertThat(menuBuilder().parentMain(view, "https://example.test/telegram"))
            .extracting(TelegramBotApiClient.InlineButton::callbackData)
            .contains("nav.requests-child-1.signed", "nav.coins-child-1.signed",
                "nav.recent-child-1.signed", "nav.child-child-1.signed");
    }

    @Test
    void parentChildCatalogExposesReadOnlyTasksAndRewards() {
        assertThat(menuBuilder().parentChildCatalog(view()))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("✅ Tasks", "🎁 Rewards", "🔄 Switch child", "⬅️ Back");
    }

    @Test
    void balanceShowsTheCurrentChildAmount() {
        assertThat(menuBuilder().balance(view()))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("🪙 Balance · 42", "⬅️ Back");
    }

    @Test
    void childMainContainsOnlyShortActionCompanionEntries() {
        assertThat(menuBuilder().childMain(new TelegramQuickActionResponse(
            "family", "child", 1, "Alex", 42, List.of(), List.of(), List.of(), List.of(), List.of()),
            "https://example.test/telegram"))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("✅ Tasks", "🎁 Rewards", "📜 Recent", "📱 Open Mini App");
    }

    @Test
    void signedLegacyParentCatalogNavigationReturnsToDecisionHome() {
        TelegramQuickActionResponse view = view();

        assertThat(TelegramMenuFlow.navigationMenu("tasks-child-1", view,
            "https://example.test/telegram", menuBuilder()))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("🎯 Requests (0)", "🪙 Coins", "📜 Recent", "🔄 Switch child", "📱 Open Mini App");
    }

    @Test
    void childTasksCapsRowsAndAddsMiniAppForMore() {
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 1, "Alex", 42, List.of(),
            java.util.stream.LongStream.range(1, 7)
                .mapToObj(id -> new com.sashplatonov.earnit.kids.dto.response.TaskDto(
                    id, "Task " + id, 5, null, null, null, null, null, null, true, 1, null, null))
                .toList(), List.of(), List.of(), List.of());

        assertThat(menuBuilder().childTasks(view, "https://example.test/telegram"))
            .hasSize(7)
            .extracting(TelegramBotApiClient.InlineButton::text)
            .contains("📱 More tasks → Mini App");
    }

    @Test
    void parentRequestsShowsBoundedApprovalActions() {
        RequestDto request = new RequestDto(
            19L, 7L, "Homework", null, null, "Homework", null, null, null, null,
            20, PurchaseRequestStatus.pending, PurchaseRequestType.earn, 0, "2026-08-13T12:00:00Z",
            1, null, null, null, null);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 1, "Alex", 42, List.of(), List.of(), List.of(), List.of(request), List.of());

        assertThat(menuBuilder().parentRequests(view))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("👍 Homework", "👎 Reject", "⬅️ Back");
    }

    @Test
    void parentChildPickerShowsAuthorisedChildren() {
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "parent", 1, "Alex", 42,
            List.of(
                new ChildDto(1, "Alex", 42, 100, 0, "ocean", List.of(), List.of(), List.of(), List.of(), null),
                new ChildDto(2, "Sam", 18, 100, 0, "forest", List.of(), List.of(), List.of(), List.of(), null)),
            List.of(), List.of(), List.of(), List.of());

        assertThat(menuBuilder().parentChildPicker(view))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("👧 Alex · 42 coins", "👧 Sam · 18 coins");
    }

    @Test
    void parentCoinsRequiresConfirmationForFixedDeltas() {
        TelegramQuickActionResponse view = view();

        assertThat(menuBuilder().parentCoins(view))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("➕ 1", "➕ 2", "➕ 5", "➕ 10", "➖ 1", "➖ 2", "➖ 5", "➖ 10", "⬅️ Back");
        assertThat(menuBuilder().parentCoinConfirmation(view, -10))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("👍 Confirm", "⬅️ Cancel");
    }

    @Test
    void childRequestsOffersRetryOnlyForRejectedTask() {
        RequestDto rejected = new RequestDto(
            19L, 7L, "Homework", null, null, "Homework", null, null, null, null,
            20, PurchaseRequestStatus.rejected, PurchaseRequestType.earn, 0, "2026-08-13T12:00:00Z",
            1, null, null, null, null);
        TelegramQuickActionResponse view = new TelegramQuickActionResponse(
            "family", "child", 1, "Alex", 42, List.of(), List.of(), List.of(), List.of(rejected), List.of());

        assertThat(menuBuilder().childRequests(view))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("👎 Not approved · Homework", "🔄 Try again", "⬅️ Back");
    }

    @Test
    void parentNoChildrenOffersOnlyMiniAppEntry() {
        assertThat(menuBuilder().parentNoChildren("https://example.test/telegram"))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("➕ Add child → Mini App");
    }

    @Test
    void recentIsBoundedAndHasEmptyState() {
        TelegramQuickActionResponse empty = view();
        assertThat(menuBuilder().recent(empty))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("No recent operations", "⬅️ Back");
    }

    private TelegramQuickActionResponse view() {
        return new TelegramQuickActionResponse(
            "family", "parent", 1, "Alex", 42,
            List.of(new ChildDto(1, "Alex", 42, 100, 0, "ocean", List.of(), List.of(), List.of(), List.of(), null)),
            List.of(), List.of(), List.of(), List.of());
    }

    private TelegramMenuBuilder menuBuilder() {
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        when(callbacks.signNavigation(anyString())).thenAnswer(invocation ->
            "nav." + invocation.getArgument(0, String.class) + ".signed");
        return new TelegramMenuBuilder(callbacks);
    }
}
