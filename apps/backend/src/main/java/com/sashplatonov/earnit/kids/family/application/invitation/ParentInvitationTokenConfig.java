package com.sashplatonov.earnit.kids.family.application.invitation;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

@ConfigMapping(prefix = "app.parent-invitation")
public interface ParentInvitationTokenConfig {
    String activeKeyId();

    String activeKey();

    Optional<String> previousKeyId();

    Optional<String> previousKey();
}
