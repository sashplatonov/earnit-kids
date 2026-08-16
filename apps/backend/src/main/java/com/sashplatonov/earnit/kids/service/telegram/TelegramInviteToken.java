package com.sashplatonov.earnit.kids.service.telegram;

// EXPLAIN: Shared pairing-token prefixes used to distinguish deep-link invite
// EXPLAIN: kinds (child invite vs parent self-link) when they arrive as the
// EXPLAIN: Telegram startapp parameter.
public final class TelegramInviteToken {
    public static final String CHILD_INVITE_PREFIX = "ci_";
    public static final String PARENT_INVITE_PREFIX = "pi_";

    private TelegramInviteToken() {
    }
}