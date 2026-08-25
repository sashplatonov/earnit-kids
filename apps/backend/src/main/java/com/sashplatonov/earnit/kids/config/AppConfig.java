package com.sashplatonov.earnit.kids.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    Auth auth();

    Performance performance();

    Observability observability();

    @WithDefault("false")
    boolean production();

    Google google();


    interface Auth {

        @WithDefault("2592000")
        int sessionTtlSeconds();

        @WithDefault("7776000")
        int refreshTokenTtlSeconds();
    }

    interface Google {

        @WithDefault("false")
        boolean enabled();

        Optional<String> clientId();

        Optional<String> clientSecret();

        Optional<String> redirectUri();
    }

    interface Performance {

        HttpMetrics httpMetrics();

        interface HttpMetrics {

            @WithDefault("true")
            boolean payloadEstimationEnabled();

            @WithDefault("256")
            int payloadEstimationMaxCollectionSize();

            @WithDefault("750")
            int slowRequestThresholdMs();

            @WithDefault("250")
            int slowQueryThresholdMs();
        }
    }

    interface Observability {

        NewRelic newRelic();

        interface NewRelic {

            @WithDefault("false")
            boolean agentEnabled();

            Metrics metrics();

            Logging logging();

            interface Metrics {

                @WithDefault("false")
                boolean enabled();

                @WithDefault("otlp.nr-data.net")
                String otlpMetricsEndpoint();

                @WithDefault("http/protobuf")
                String otlpMetricsProtocol();

                Optional<String> licenseKey();
            }

            interface Logging {

                @WithDefault("false")
                boolean forwardingEnabled();

                @WithDefault("10000")
                int forwardingMaxSamplesStored();

                @WithDefault("false")
                boolean localDecoratingEnabled();
            }
        }
    }
}
