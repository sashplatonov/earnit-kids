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
                                      boolean emailVerificationEnabled,
                                      boolean passwordRecoveryEnabled) {
        return appConfig(
            production,
            superAdminEmail,
            emailVerificationEnabled,
            passwordRecoveryEnabled,
            false,
            null);
    }

    public static AppConfig appConfig(boolean production,
                                      String superAdminEmail,
                                      boolean emailVerificationEnabled,
                                      boolean passwordRecoveryEnabled,
                                      boolean googleEnabled,
                                      String googleClientId) {
        return appConfig(
            production,
            superAdminEmail,
            emailVerificationEnabled,
            passwordRecoveryEnabled,
            googleEnabled,
            googleClientId,
            null);
    }

    public static AppConfig appConfig(boolean production,
                                      String superAdminEmail,
                                      boolean emailVerificationEnabled,
                                      boolean passwordRecoveryEnabled,
                                      boolean googleEnabled,
                                      String googleClientId,
                                      String googleClientSecret) {
        return appConfig(
            production,
            superAdminEmail,
            emailVerificationEnabled,
            passwordRecoveryEnabled,
            googleEnabled,
            googleClientId,
            googleClientSecret,
            null,
            2592000,
            7776000);
    }

    public static AppConfig appConfig(boolean production,
                                      String superAdminEmail,
                                      boolean emailVerificationEnabled,
                                      boolean passwordRecoveryEnabled,
                                      boolean googleEnabled,
                                      String googleClientId,
                                      String googleClientSecret,
                                      String googleRedirectUri,
                                      int sessionTtlSeconds,
                                      int refreshTokenTtlSeconds) {
        return new AppConfig() {
            @Override
            public Auth auth() {
                return new Auth() {
                    @Override
                    public int sessionTtlSeconds() {
                        return sessionTtlSeconds;
                    }

                    @Override
                    public int refreshTokenTtlSeconds() {
                        return refreshTokenTtlSeconds;
                    }
                };
            }

            @Override
            public boolean production() {
                return production;
            }

            @Override
            public SuperAdmin superAdmin() {
                return () -> Optional.ofNullable(superAdminEmail);
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

                    @Override
                    public Optional<String> clientSecret() {
                        return Optional.ofNullable(googleClientSecret);
                    }

                    @Override
                    public Optional<String> redirectUri() {
                        return Optional.ofNullable(googleRedirectUri);
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
