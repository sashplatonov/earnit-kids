package com.sashplatonov.earnit.kids.support;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.JwtCompatibilityConfig;

import java.util.Optional;

public final class TestConfigFactory {

    private TestConfigFactory() {
    }

    public static AppConfig appConfig(boolean production,
                                      String superAdminEmail,
                                      String superAdminPassword,
                                      boolean emailVerificationEnabled,
                                      boolean passwordRecoveryEnabled) {
        return new AppConfig() {
            @Override
            public boolean production() {
                return production;
            }

            @Override
            public SuperAdmin superAdmin() {
                return new SuperAdmin() {
                    @Override
                    public Optional<String> email() {
                        return Optional.ofNullable(superAdminEmail);
                    }

                    @Override
                    public Optional<String> password() {
                        return Optional.ofNullable(superAdminPassword);
                    }
                };
            }

            @Override
            public EmailVerification emailVerification() {
                return () -> emailVerificationEnabled;
            }

            @Override
            public PasswordRecovery passwordRecovery() {
                return () -> passwordRecoveryEnabled;
            }
        };
    }

    public static JwtCompatibilityConfig jwtConfig(String secret) {
        return () -> secret;
    }
}