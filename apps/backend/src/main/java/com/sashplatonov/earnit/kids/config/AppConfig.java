package com.sashplatonov.earnit.kids.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    Auth auth();

    @WithDefault("false")
    boolean production();

    SuperAdmin superAdmin();

    EmailVerification emailVerification();

    PasswordRecovery passwordRecovery();

    Google google();

    interface Auth {

        @WithDefault("2592000")
        int sessionTtlSeconds();

        @WithDefault("7776000")
        int refreshTokenTtlSeconds();
    }

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

        Optional<String> redirectUri();
    }
}
