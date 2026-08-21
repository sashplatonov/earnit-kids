package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;

public final class ParentInviteAcceptRequest {
    @NotBlank private final String token;
    @NotBlank private final String initData;
    private final String legacyEmail;

    public ParentInviteAcceptRequest(String token, String initData) {
        this(token, initData, null, true);
    }

    // EXPLAIN: Preserve source compatibility for callers compiled against the email-based contract.
    public ParentInviteAcceptRequest(String token, String ignoredEmail, String initData) {
        this(token, initData, ignoredEmail, true);
    }

    private ParentInviteAcceptRequest(String token, String initData, String legacyEmail, boolean ignored) {
        this.token = token;
        this.initData = initData;
        this.legacyEmail = legacyEmail;
    }

    public String token() {
        return token;
    }

    public String initData() {
        return initData;
    }

    public String legacyEmail() {
        return legacyEmail;
    }
}
