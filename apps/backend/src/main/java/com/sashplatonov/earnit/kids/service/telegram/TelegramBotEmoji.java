package com.sashplatonov.earnit.kids.service.telegram;

// EXPLAIN: Semantic emoji vocabulary for the Telegram bot. Every inline/menu
// EXPLAIN: button carries exactly one emoji from this map; emoji literals must
// EXPLAIN: not be scattered across handlers or menu builders. The mapping is
// EXPLAIN: deterministic and is enforced by TelegramEmojiCoverageTest.
public final class TelegramBotEmoji {
    public static final String HOME = "🏠";
    public static final String TASKS = "✅";
    public static final String REWARDS = "🎁";
    public static final String REQUESTS = "🎯";
    public static final String APPROVE = "👍";
    public static final String REJECT = "👎";
    public static final String COINS = "🪙";
    public static final String RECENT = "📜";
    public static final String CHILD = "👧";
    public static final String SWITCH = "🔄";
    public static final String MINI_APP = "📱";
    public static final String ADD = "➕";
    public static final String REMOVE = "➖";
    public static final String CUSTOM = "🔢";
    public static final String WAITING = "⏳";
    public static final String SUCCESS = "✅";
    public static final String NEXT = "➡️";
    public static final String ERROR = "⚠️";
    public static final String INFO = "ℹ️";
    public static final String REFRESH = "🔄";
    public static final String TASK_DONE = "☀️";
    public static final String TASK_LEARN = "📖";
    public static final String GREETING = "👋";
    public static final String CELEBRATE = "🎉";
    public static final String DECLINE = "❌";

    // EXPLAIN: Legacy aliases kept during the UX migration; new code must use
    // EXPLAIN: the semantic names above.
    @Deprecated public static final String DONE = TASKS;
    @Deprecated public static final String REWARD = REWARDS;
    @Deprecated public static final String REQUEST = REQUESTS;
    @Deprecated public static final String OPEN_APP = MINI_APP;

    private TelegramBotEmoji() {
    }
}

