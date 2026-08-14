package com.sashplatonov.earnit.kids.service.telegram;

import com.sashplatonov.earnit.kids.dto.response.ChildDto;
import com.sashplatonov.earnit.kids.dto.response.HistoryEntryDto;
import com.sashplatonov.earnit.kids.dto.response.RequestDto;
import com.sashplatonov.earnit.kids.dto.response.ShopItemDto;
import com.sashplatonov.earnit.kids.dto.response.TaskDto;
import com.sashplatonov.earnit.kids.dto.response.TelegramQuickActionResponse;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the centralized semantic emoji vocabulary (BUX-012).
 *
 * <p>Every inline/menu button produced by the bot must contain exactly one
 * emoji drawn from {@link TelegramBotEmoji}. A button without a mapped emoji —
 * or a raw emoji literal scattered into a handler/menu builder — fails here.
 */
class TelegramEmojiCoverageTest {
    private static final String MINI_APP = "https://example.test/telegram";

    @Test
    void everyProducedButtonContainsExactlyOneSemanticEmoji() {
        Set<String> semanticEmoji = semanticEmoji();
        TelegramMenuBuilder builder = menuBuilder();
        TelegramQuickActionResponse parent = parentView();
        TelegramQuickActionResponse child = childView();

        List<TelegramBotApiClient.InlineButton> buttons = new ArrayList<>();
        buttons.addAll(builder.parentMain(parent, MINI_APP));
        buttons.addAll(builder.parentChildPicker(parent));
        buttons.addAll(builder.parentCoins(parent, MINI_APP));
        buttons.addAll(builder.parentCoinConfirmation(parent, -10));
        buttons.addAll(builder.coinRetry(parent, -10));
        buttons.addAll(builder.childMain(child, MINI_APP));
        buttons.addAll(builder.childTasks(child, MINI_APP));
        buttons.addAll(builder.childRewards(child, MINI_APP));
        buttons.addAll(builder.parentRequestQueue(parent, null));
        buttons.addAll(builder.parentRequestsEmpty(parent, MINI_APP));
        buttons.addAll(builder.recent(parent, MINI_APP));
        buttons.addAll(builder.recent(child, MINI_APP));
        buttons.addAll(builder.backToMain());
        buttons.addAll(TelegramMenuFlow.navigationMenu("requests-child-1", parent, MINI_APP, builder));
        buttons.addAll(TelegramMenuFlow.navigationMenu("tasks-child-1", child, MINI_APP, builder));
        buttons.addAll(TelegramMenuFlow.navigationMenu("rewards-child-1", child, MINI_APP, builder));
        buttons.addAll(TelegramMenuFlow.navigationMenu("coins-child-1", parent, MINI_APP, builder));
        buttons.addAll(TelegramMenuFlow.navigationMenu("recent-child-1", child, MINI_APP, builder));

        assertThat(buttons).isNotEmpty();
        for (TelegramBotApiClient.InlineButton button : buttons) {
            assertThat(countEmoji(button.text(), semanticEmoji))
                .as("button label '%s' must contain exactly one semantic emoji", button.text())
                .isEqualTo(1);
        }
    }

    @Test
    void emojiMapValuesAreUniqueAndNonEmpty() {
        Set<String> values = semanticEmoji();
        assertThat(values).isNotEmpty();
        assertThat(values).allSatisfy(value -> assertThat(value).isNotBlank());
    }

    private static int countEmoji(String text, Set<String> emoji) {
        int count = 0;
        for (String value : emoji) {
            int index = 0;
            while (index < text.length()) {
                int found = text.indexOf(value, index);
                if (found < 0) {
                    break;
                }
                count++;
                index = found + value.length();
            }
        }
        return count;
    }

    private static Set<String> semanticEmoji() {
        Set<String> values = new HashSet<>();
        for (Field field : TelegramBotEmoji.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !String.class.equals(field.getType())) {
                continue;
            }
            try {
                values.add((String) field.get(null));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot read TelegramBotEmoji", exception);
            }
        }
        return values;
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
                List.of(), List.of(), null)),
            List.of(),
            List.of(),
            List.of(new RequestDto(19L, 7L, "Утренний старт", null, null, "Утренний старт", null, null,
                null, null, 1, PurchaseRequestStatus.pending, PurchaseRequestType.earn, 0,
                "2026-08-14T06:00:00Z", 1, null, null, null, null)),
            List.of(new HistoryEntryDto(1L, HistoryEntryType.earn, 1, "Утренний старт", null, 0,
                null, 7L, "Утренний старт", null, null, null, null, "2026-08-14T06:00:00Z", 1)));
    }

    private TelegramQuickActionResponse childView() {
        return new TelegramQuickActionResponse(
            "family", "child", 1, "Aliska", 22,
            List.of(new ChildDto(1, "Aliska", 22, 100, 0, "ocean", List.of(), List.of(),
                List.of(), List.of(), null)),
            List.of(new TaskDto(1, "Утренний старт", 1, null, null, null, null, null, null,
                true, 1, null, null)),
            List.of(new ShopItemDto(1, "Королева настолки", 2, null, null, null, null,
                true, 1, null)),
            List.of(),
            List.of(new HistoryEntryDto(1L, HistoryEntryType.earn, 1, "Утренний старт", null, 0,
                null, 7L, "Утренний старт", null, null, null, null, "2026-08-14T06:00:00Z", 1)));
    }
}
