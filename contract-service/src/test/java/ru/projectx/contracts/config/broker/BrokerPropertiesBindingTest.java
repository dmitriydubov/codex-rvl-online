package ru.projectx.contracts.config.broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class BrokerPropertiesBindingTest {

    private static final String INTERNAL = "projectx.brokers.internal";
    private static final String ONEC = "projectx.brokers.onec";

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(Brokers.class);

    @Test
    void completeConfiguration_bindsBothBrokers() {
        contexts.withPropertyValues(both()).run(context -> {
            InternalBrokerProperties internal = context.getBean(InternalBrokerProperties.class);
            OneCBrokerProperties onec = context.getBean(OneCBrokerProperties.class);

            assertThat(internal.connection().host()).isEqualTo("internal-broker");
            assertThat(internal.connection().port()).isEqualTo(5672);
            assertThat(internal.connection().virtualHost()).isEqualTo("/");
            assertThat(internal.connection().username()).isEqualTo("contract_service");
            assertThat(internal.connection().password()).isEqualTo("secret");
            assertThat(internal.connection().connectionTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(internal.listenerAutoStartup()).isTrue();
            assertThat(internal.queues().taskChanges()).isEqualTo("internal-in");
            assertThat(internal.queues().statusChanges()).isEqualTo("internal-out");
            assertThat(onec.connection().host()).isEqualTo("onec-broker");
            assertThat(onec.listenerAutoStartup()).isFalse();
            assertThat(onec.queues().kmContractSubmissions()).isEqualTo("onec-km-out");
            assertThat(onec.queues().kmContractRegistrations()).isEqualTo("onec-km-in");
            assertThat(onec.queues().lmPolicyRequests()).isEqualTo("onec-lm-out");
            assertThat(onec.queues().lmPolicyIssues()).isEqualTo("onec-lm-in");
        });
    }

    @Test
    void tls_isOffByDefaultAndVerifiesCertificateAndHostnameOnceEnabled() {
        contexts.withPropertyValues(both()).run(context -> {
            BrokerConnectionProperties.Tls tls = context.getBean(InternalBrokerProperties.class).connection().tls();

            assertThat(tls.enabled()).isFalse();
            assertThat(tls.validateServerCertificate()).isTrue();
            assertThat(tls.verifyHostname()).isTrue();
        });
    }

    @Test
    void tlsChecks_canBeSwitchedOffExplicitly() {
        contexts.withPropertyValues(both())
                .withPropertyValues(ONEC + ".connection.tls.enabled=true",
                        ONEC + ".connection.tls.validate-server-certificate=false",
                        ONEC + ".connection.tls.verify-hostname=false")
                .run(context -> {
                    BrokerConnectionProperties.Tls tls = context.getBean(OneCBrokerProperties.class).connection().tls();

                    assertThat(tls.enabled()).isTrue();
                    assertThat(tls.validateServerCertificate()).isFalse();
                    assertThat(tls.verifyHostname()).isFalse();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {INTERNAL, ONEC})
    void emptyPassword_failsStartup(String prefix) {
        contexts.withPropertyValues(both()).withPropertyValues(prefix + ".connection.password=")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {INTERNAL, ONEC})
    void absentPassword_failsStartup(String prefix) {
        String[] withoutPassword = Arrays.stream(both())
                .filter(value -> !value.startsWith(prefix + ".connection.password="))
                .toArray(String[]::new);

        contexts.withPropertyValues(withoutPassword).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "65536"})
    void portOutOfRange_failsStartup(String port) {
        contexts.withPropertyValues(both()).withPropertyValues(INTERNAL + ".connection.port=" + port)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingQueueName_failsStartup() {
        contexts.withPropertyValues(both()).withPropertyValues(ONEC + ".queues.lm-policy-issues=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void sameQueueNameInTwoProperties_failsStartupNamingTheQueue() {
        contexts.withPropertyValues(both()).withPropertyValues(INTERNAL + ".queues.status-changes=internal-in")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessage("имя очереди задано двум свойствам: internal-in");
                });
    }

    @Test
    void sameOneCQueueNameInTwoProperties_failsStartup() {
        contexts.withPropertyValues(both()).withPropertyValues(ONEC + ".queues.lm-policy-issues=onec-km-in")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessage("имя очереди задано двум свойствам: onec-km-in");
                });
    }

    private static String[] both() {
        List<String> values = new ArrayList<>(connection(INTERNAL, "internal-broker"));
        values.addAll(connection(ONEC, "onec-broker"));
        values.addAll(List.of(
                INTERNAL + ".listener-auto-startup=true",
                INTERNAL + ".queues.task-changes=internal-in",
                INTERNAL + ".queues.status-changes=internal-out",
                ONEC + ".listener-auto-startup=false",
                ONEC + ".queues.km-contract-submissions=onec-km-out",
                ONEC + ".queues.km-contract-registrations=onec-km-in",
                ONEC + ".queues.lm-policy-requests=onec-lm-out",
                ONEC + ".queues.lm-policy-issues=onec-lm-in"));
        return values.toArray(String[]::new);
    }

    private static List<String> connection(String prefix, String host) {
        return List.of(
                prefix + ".connection.host=" + host,
                prefix + ".connection.port=5672",
                prefix + ".connection.virtual-host=/",
                prefix + ".connection.username=contract_service",
                prefix + ".connection.password=secret",
                prefix + ".connection.connection-timeout=10s");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({InternalBrokerProperties.class, OneCBrokerProperties.class})
    static class Brokers {
    }
}
