package com.sashplatonov.earnit.kids.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    @WithDefault("false")
    boolean production();

    SuperAdmin superAdmin();

    EmailVerification emailVerification();

    PasswordRecovery passwordRecovery();

    Google google();

    interface SuperAdmin {

        Optional<String> email();

        Optional<String> password();
    }

    interface EmailVerification {

        @WithDefault("true")
        boolean enabled();
    }

    interface PasswordRecovery {

        @WithDefault("true")
        boolean enabled();
    }

    interface Google {

        @WithDefault("false")
        boolean enabled();

        Optional<String> clientId();
        Optional<String> clientSecret();
    }
}
