package com.sashplatonov.earnit.kids.config.observability;

import com.sashplatonov.earnit.kids.support.TestConfigFactory;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import com.sashplatonov.earnit.kids.config.AppConfig;
class ReadinessChecksTest {

    @Test
    void newRelicMetricsReadiness_isUpWhenMetricsDisabled() {
        NewRelicMetricsReadinessCheck check = new NewRelicMetricsReadinessCheck(
            TestConfigFactory.appConfig(false, null, false, false)
        );

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
    }

    @Test
    void newRelicMetricsReadiness_isDownWhenEnabledWithoutLicense() {
        NewRelicMetricsReadinessCheck check = new NewRelicMetricsReadinessCheck(appConfig(true, Optional.empty()));

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
    }

    private static AppConfig appConfig(boolean metricsEnabled, Optional<String> licenseKey) {
        AppConfig base = TestConfigFactory.appConfig(false, null, false, false);
        return new AppConfig() {
            @Override
            public Auth auth() {
                return base.auth();
            }

            @Override
            public Performance performance() {
                return base.performance();
            }

            @Override
            public Observability observability() {
                return () -> new Observability.NewRelic() {
                    @Override
                    public boolean agentEnabled() {
                        return false;
                    }

                    @Override
                    public Metrics metrics() {
                        return new Metrics() {
                            @Override
                            public boolean enabled() {
                                return metricsEnabled;
                            }

                            @Override
                            public String otlpMetricsEndpoint() {
                                return "https://otlp.nr-data.net";
                            }

                            @Override
                            public String otlpMetricsProtocol() {
                                return "http/protobuf";
                            }

                            @Override
                            public Optional<String> licenseKey() {
                                return licenseKey;
                            }
                        };
                    }

                    @Override
                    public Logging logging() {
                        return base.observability().newRelic().logging();
                    }
                };
            }

            @Override
            public boolean production() {
                return base.production();
            }

            @Override
            public SuperAdmin superAdmin() {
                return base.superAdmin();
            }

            @Override
            public EmailVerification emailVerification() {
                return base.emailVerification();
            }

            @Override
            public PasswordRecovery passwordRecovery() {
                return base.passwordRecovery();
            }

            @Override
            public Google google() {
                return base.google();
            }

        };
    }
}
