package com.sashplatonov.earnit.kids.platform.webpush;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

@ConfigMapping(prefix = "app.web-push")
public interface WebPushConfig {
    @WithDefault("") Optional<String> vapidPublicKey();
    @WithDefault("") Optional<String> vapidPrivateKey();
    @WithDefault("") Optional<String> vapidSubject();
    @WithDefault("false") boolean enabled();
    @WithDefault("5") int maxAttempts();
}
