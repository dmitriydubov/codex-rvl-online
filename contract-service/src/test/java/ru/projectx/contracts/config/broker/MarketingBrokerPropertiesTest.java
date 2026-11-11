package ru.projectx.contracts.config.broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class MarketingBrokerPropertiesTest {

    private static final String PREFIX = "projectx.brokers.marketing.";
    private static final String[] COMPLETE = {
        "projectx.payment.enabled=true",
        PREFIX + "listener-auto-startup=true",
        PREFIX + "connection.host=marketing-broker", PREFIX + "connection.port=5672",
        PREFIX + "connection.virtual-host=/", PREFIX + "connection.username=contract_service",
        PREFIX + "connection.password=secret", PREFIX + "connection.connection-timeout=10s",
        PREFIX + "document-fixes.exchange=rvl.exchange", PREFIX + "document-fixes.routing-key=rvl-contract-fixed",
        PREFIX + "order-events.queue=order.event.rvl.topic"};

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(Marketing.class);

    @Test
    void completeConfiguration_bindsTheConnectionTheListenerSwitchTheFixRouteAndTheOrderEventQueue() {
        contexts.withPropertyValues(COMPLETE).run(context -> {
            MarketingBrokerProperties marketing = context.getBean(MarketingBrokerProperties.class);

            assertThat(marketing.connection().host()).isEqualTo("marketing-broker");
            assertThat(marketing.listenerAutoStartup()).isTrue();
            assertThat(marketing.documentFixes())
                    .isEqualTo(new MarketingBrokerProperties.DocumentFixes("rvl.exchange", "rvl-contract-fixed"));
            assertThat(marketing.orderEvents())
                    .isEqualTo(new MarketingBrokerProperties.OrderEvents("order.event.rvl.topic"));
        });
    }

    @Test
    void listenerSwitch_canBeTurnedOffAndIsOffWhenNotGiven() {
        contexts.withPropertyValues(COMPLETE).withPropertyValues(PREFIX + "listener-auto-startup=false")
                .run(context -> assertThat(context.getBean(MarketingBrokerProperties.class).listenerAutoStartup())
                        .isFalse());
        contexts.withPropertyValues(without(PREFIX + "listener-auto-startup"))
                .run(context -> assertThat(context.getBean(MarketingBrokerProperties.class).listenerAutoStartup())
                        .isFalse());
    }

    @ParameterizedTest
    @ValueSource(strings = {"order-events.queue=", "order-events.queue=   ", "document-fixes.exchange=",
        "document-fixes.routing-key="})
    void blankName_failsTheStart(String setting) {
        contexts.withPropertyValues(COMPLETE).withPropertyValues(PREFIX + setting)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"order-events.", "document-fixes.", "connection."})
    void absentSection_failsTheStart(String section) {
        contexts.withPropertyValues(without(PREFIX + section)).run(context -> assertThat(context).hasFailed());
    }

    private static String[] without(String prefix) {
        return Arrays.stream(COMPLETE).filter(value -> !value.startsWith(prefix)).toArray(String[]::new);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MarketingBrokerProperties.class)
    static class Marketing {
    }
}
