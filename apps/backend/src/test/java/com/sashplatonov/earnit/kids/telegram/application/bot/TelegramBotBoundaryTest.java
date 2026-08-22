package com.sashplatonov.earnit.kids.telegram.application.bot;
import com.sashplatonov.earnit.kids.telegram.application.callback.TelegramCallbackService;
import com.sashplatonov.earnit.kids.telegram.application.identity.TelegramIdentityService;

import com.sashplatonov.earnit.kids.family.api.response.ChildDto;
import com.sashplatonov.earnit.kids.telegram.api.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.family.application.action.FamilyActionService;
import com.sashplatonov.earnit.kids.util.OperationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Boundary regression tests (BUX-015): the Parent bot never exposes Tasks/Rewards
 * catalogs, a Balance screen, catalog CRUD or a Child submenu; the Child bot never
 * exposes coins adjustment, child switching, request management or parent actions.
 */
class TelegramBotBoundaryTest {
    private static final String MINI_APP = "https://example.test/telegram";

    @Test
    void parentCannotReachTaskOrRewardCatalogs() {
        TelegramQuickActionResponse parent = parentView();
        TelegramMenuBuilder builder = menuBuilder();

        assertThat(TelegramMenuFlow.navigationMenu("tasks-child-1", parent, MINI_APP, "", builder))
            .isEmpty();
        assertThat(TelegramMenuFlow.navigationMenu("rewards-child-1", parent, MINI_APP, "", builder))
            .isEmpty();
    }

    @Test
    void parentHasNoBalanceScreenOrChildSubmenu() {
        TelegramQuickActionResponse parent = parentView();
        TelegramMenuBuilder builder = menuBuilder();

        assertThat(TelegramMenuFlow.navigationMenu("balance-child-1", parent, MINI_APP, "", builder))
            .isEmpty();
        assertThat(TelegramMenuFlow.navigationMenu("switch-child-1", parent, MINI_APP, "", builder))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .containsExactly("👧 Aliska · 22");
    }

    @Test
    void childCannotReachParentOnlyControls() {
        TelegramQuickActionResponse child = childView();
        TelegramMenuBuilder builder = menuBuilder();

        for (String action : List.of("coins-child-1", "requests-child-1", "balance-child-1")) {
            assertThat(TelegramMenuFlow.navigationMenu(action, child, MINI_APP, "", builder))
                .isEmpty();
        }
        // A stale switch action is safe for a child: it exposes no parent controls.
        assertThat(TelegramMenuFlow.navigationMenu("switch-child-1", child, MINI_APP, "", builder))
            .extracting(TelegramBotApiClient.InlineButton::text)
            .isEmpty();
    }

    @Test
    void childCannotApproveOrAdjustBalance() {
        var service = quickActions(childIdentity());
        assertThat(((OperationResult.Failure<TelegramQuickActionResponse>) service.approveRequest(77L, 42, 19L)).errorCode())
            .isEqualTo("TELEGRAM_SCOPE");
        assertThat(((OperationResult.Failure<TelegramQuickActionResponse>) service.adjustBalance(77L, 42, -5)).errorCode())
            .isEqualTo("TELEGRAM_SCOPE");
        verify(actions, never()).approveRequest(any(), anyInt(), anyLong());
        verify(actions, never()).adjustBalance(any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void parentCannotSubmitChildTaskOrRewardRequest() {
        var parent = new TelegramIdentityService.TelegramIdentity(1, 10, null, 77L, "parent");
        var service = quickActions(parent);
        assertThat(((OperationResult.Failure<TelegramQuickActionResponse>) service.requestTask(77L, 42, 5L)).errorCode())
            .isEqualTo("TELEGRAM_SCOPE");
        assertThat(((OperationResult.Failure<TelegramQuickActionResponse>) service.requestReward(77L, 42, 9L)).errorCode())
            .isEqualTo("TELEGRAM_SCOPE");
        verify(actions, never()).requestTaskCompletion(any(), anyInt(), anyLong(), any());
        verify(actions, never()).requestItemPurchase(any(), anyInt(), anyLong(), any());
    }

    @Test
    void childCannotActOnAnotherChild() {
        var service = quickActions(childIdentity());
        assertThat(((OperationResult.Failure<TelegramQuickActionResponse>) service.requestTask(77L, 99, 5L)).errorCode())
            .isEqualTo("TELEGRAM_SCOPE");
        verify(actions, never()).requestTaskCompletion(any(), anyInt(), anyLong(), any());
    }

    private final FamilyActionService actions = mock(FamilyActionService.class);

    private TelegramQuickActionServiceImpl quickActions(TelegramIdentityService.TelegramIdentity identity) {
        TelegramIdentityService identities = mock(TelegramIdentityService.class);
        FamilyRepository families = mock(FamilyRepository.class);
        FamilyService familyService = mock(FamilyService.class);
        when(identities.findActiveByTelegramUserId(77L)).thenReturn(Optional.of(identity));
        return new TelegramQuickActionServiceImpl(identities, families, familyService, actions);
    }

    private TelegramIdentityService.TelegramIdentity childIdentity() {
        return new TelegramIdentityService.TelegramIdentity(1, 10, 42, 77L, "child");
    }

    private TelegramMenuBuilder menuBuilder() {
        TelegramCallbackService callbacks = mock(TelegramCallbackService.class);
        when(callbacks.signNavigation(anyString())).thenAnswer(invocation ->
            "nav." + invocation.getArgument(0, String.class) + ".signed");
        return new TelegramMenuBuilder(callbacks);
    }

    private TelegramQuickActionResponse parentView() {
        return new TelegramQuickActionResponse(
            "family", "parent", 1, "Aliska", 22,
            List.of(new ChildDto(1, "Aliska", 22, 100, 0, "ocean", List.of(), List.of(),
                List.of(), List.of())),
            List.of(), List.of(), List.of(), List.of());
    }

    private TelegramQuickActionResponse childView() {
        return new TelegramQuickActionResponse(
            "family", "child", 1, "Aliska", 22,
            List.of(new ChildDto(1, "Aliska", 22, 100, 0, "ocean", List.of(), List.of(),
                List.of(), List.of())),
            List.of(), List.of(), List.of(), List.of());
    }
}
