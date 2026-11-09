package ru.projectx.contracts.config.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class OrderingPropertiesTest {

    private static final Map<String, String> REQUIRED = Map.ofEntries(
            Map.entry("projectx.ordering.url", "http://ordering.test"),
            Map.entry("projectx.ordering.connect-timeout", "2s"),
            Map.entry("projectx.ordering.read-timeout", "10s"),
            Map.entry("projectx.ordering.product", "rvl"),
            Map.entry("projectx.ordering.channel", "rvl-lk"),
            Map.entry("projectx.ordering.retry.attempts", "3"),
            Map.entry("projectx.ordering.retry.delay", "1s"),
            Map.entry("projectx.ordering.document.zone", "Europe/Moscow"),
            Map.entry("projectx.ordering.document.ttl-before-check-in", "1s"),
            Map.entry("projectx.ordering.token.uri", "http://keycloak.test/token"),
            Map.entry("projectx.ordering.token.client-id", "contracts-stand"),
            Map.entry("projectx.ordering.token.client-secret", "stand-secret-7f3a"),
            Map.entry("projectx.ordering.token.connect-timeout", "2s"),
            Map.entry("projectx.ordering.token.read-timeout", "5s"));

    @Test
    void cancelReason_defaultsToNotActual() {
        OrderingProperties bound = new Binder(new MapConfigurationPropertySource(REQUIRED))
                .bind("projectx.ordering", OrderingProperties.class).get();

        assertThat(bound.cancelReason()).isEqualTo("notActual");
        assertThat(bound.product()).isEqualTo("rvl");
    }

    @Test
    void tokenSettings_neverPrintTheSecret() {
        OrderingProperties bound = new Binder(new MapConfigurationPropertySource(REQUIRED))
                .bind("projectx.ordering", OrderingProperties.class).get();

        assertThat(bound.token().toString()).doesNotContain("stand-secret-7f3a")
                .contains("contracts-stand", "http://keycloak.test/token");
        assertThat(bound.toString()).doesNotContain("stand-secret-7f3a");
    }
}
