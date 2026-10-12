package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.config.BrokerConnectionProperties;

/**
 * Подключение к брокеру собирается ровно из своих настроек: адрес, учётные данные, таймаут и TLS доходят до клиента,
 * подтверждения и возвраты включены, а имя подключения называет сервис и брокер. Без этого ошибка в одном брокере
 * выглядела бы как ошибка в другом. Флаги TLS и стратегия имени у библиотеки геттеров не имеют и читаются из полей:
 * наблюдаемы они только на настоящем брокере с TLS.
 */
class BrokerConnectionFactoriesTest {

    private static final int PORT = 5671;
    private static final Duration TIMEOUT = Duration.ofSeconds(7);

    @Test
    void connectionFactory_carriesAddressCredentialsConfirmsAndReturns() {
        CachingConnectionFactory factory = BrokerConnectionFactories.create("onec", properties(tls(false, true, true)));
        try {
            assertThat(factory.getHost()).isEqualTo("broker.example");
            assertThat(factory.getPort()).isEqualTo(PORT);
            assertThat(factory.getVirtualHost()).isEqualTo("/contracts");
            assertThat(factory.getUsername()).isEqualTo("contract_service");
            assertThat(factory.getRabbitConnectionFactory().getPassword()).isEqualTo("secret");
            assertThat(factory.getRabbitConnectionFactory().getConnectionTimeout()).isEqualTo(TIMEOUT.toMillis());
            assertThat(factory.isPublisherConfirms()).isTrue();
            assertThat(factory.isSimplePublisherConfirms()).isFalse();
            assertThat(factory.isPublisherReturns()).isTrue();
        }
        finally {
            factory.destroy();
        }
    }

    /** Клиент инициализируется при сборке подключения: иначе TLS из настроек не дошёл бы до клиента. */
    @Test
    void connectionFactory_appliesTlsToInitialisedClient() {
        CachingConnectionFactory factory = BrokerConnectionFactories.create("onec", properties(tls(true, true, true)));
        try {
            assertThat(factory.getRabbitConnectionFactory().isSSL()).isTrue();
        }
        finally {
            factory.destroy();
        }
    }

    /** Имя видно администратору брокера в списке подключений: по нему ясно, какой сервис и к какому брокеру пришёл. */
    @Test
    void connectionName_namesServiceAndBroker() {
        CachingConnectionFactory factory = BrokerConnectionFactories.create("onec", properties(tls(false, true, true)));
        try {
            ConnectionNameStrategy strategy = (ConnectionNameStrategy) ReflectionTestUtils.getField(
                    factory, "connectionNameStrategy");

            assertThat(strategy.obtainNewConnectionName(factory)).isEqualTo("contract-service:onec");
        }
        finally {
            factory.destroy();
        }
    }

    @Test
    void tlsOff_leavesClientWithoutSsl() {
        RabbitConnectionFactoryBean client = BrokerConnectionFactories.clientFactory(properties(tls(false, true, true)));

        assertThat(client).hasFieldOrPropertyWithValue("useSSL", false);
        client.afterPropertiesSet();
        assertThat(client.getRabbitConnectionFactory().isSSL()).isFalse();
    }

    @Test
    void tlsOn_verifiesCertificateAndHostnameByDefault() {
        RabbitConnectionFactoryBean client = BrokerConnectionFactories.clientFactory(properties(tls(true, true, true)));

        assertThat(client)
                .hasFieldOrPropertyWithValue("useSSL", true)
                .hasFieldOrPropertyWithValue("skipServerCertificateValidation", false)
                .hasFieldOrPropertyWithValue("enableHostnameVerification", true);
        client.afterPropertiesSet();
        assertThat(client.getRabbitConnectionFactory().isSSL()).isTrue();
    }

    /** Самоподписанный сертификат тестового контура: проверки выключают явно, и клиент это получает. */
    @Test
    void tlsChecksOff_reachTheClient() {
        RabbitConnectionFactoryBean client = BrokerConnectionFactories.clientFactory(properties(tls(true, false, false)));

        assertThat(client)
                .hasFieldOrPropertyWithValue("skipServerCertificateValidation", true)
                .hasFieldOrPropertyWithValue("enableHostnameVerification", false);
    }

    private static BrokerConnectionProperties properties(BrokerConnectionProperties.Tls tls) {
        return new BrokerConnectionProperties(
                "broker.example", PORT, "/contracts", "contract_service", "secret", TIMEOUT, tls);
    }

    private static BrokerConnectionProperties.Tls tls(boolean enabled, boolean validate, boolean verifyHostname) {
        return new BrokerConnectionProperties.Tls(enabled, validate, verifyHostname);
    }
}
