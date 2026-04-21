package com.sashplatonov.earnit.kids.support;

import com.sashplatonov.earnit.kids.config.AppConfig;
import com.sashplatonov.earnit.kids.config.JwtCompatibilityConfig;
import com.sashplatonov.earnit.kids.util.TimeProvider;

import java.time.Instant;
import java.util.Optional;

public final class TestConfigFactory {

    private TestConfigFactory() {
    }

    public static AppConfig appConfig(boolean production,
                                      String superAdminEmail,
                                      String superAdminPassword,
                                      boolean emailVerificationEnabled,
                                      boolean passwordRecoveryEnabled) {
        return appConfig(
            production,
            superAdminEmail,
            superAdminPassword,
            emailVerificationEnabled,
            passwordRecoveryEnabled,
            false,
            null);
    }

    public static AppConfig appConfig(boolean production,
                                      String superAdminEmail,
                                      String superAdminPassword,
                                      boolean emailVerificationEnabled,
                                      boolean passwordRecoveryEnabled,
                                      boolean googleEnabled,
                                      String googleClientId) {
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

            @Override
            public Google google() {
                return new Google() {
                    @Override
                    public boolean enabled() {
                        return googleEnabled;
                    }

                    @Override
                    public Optional<String> clientId() {
                        return Optional.ofNullable(googleClientId);
                    }
                };
            }
        };
    }

    public static JwtCompatibilityConfig jwtConfig(String secret) {
        return () -> secret;
    }

    public static TimeProvider timeProvider(Instant instant) {
        return () -> instant;
    }
}