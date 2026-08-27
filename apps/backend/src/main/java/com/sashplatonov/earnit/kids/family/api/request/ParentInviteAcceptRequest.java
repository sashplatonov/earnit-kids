package com.sashplatonov.earnit.kids.family.api.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public final class ParentInviteAcceptRequest {
    @NotBlank private final String token;
    @NotBlank private final String initData;
    private final String legacyEmail;

    public ParentInviteAcceptRequest(String token, String initData) {
        this(token, initData, null, true);
    }

    // EXPLAIN: Preserve source compatibility for callers compiled against the email-based contract.
    @JsonCreator
    public ParentInviteAcceptRequest(
            @JsonProperty("token") String token,
            @JsonProperty("legacyEmail") String ignoredEmail,
            @JsonProperty("initData") String initData) {
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
