package ru.projectx.contracts.service.integration;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import ru.projectx.contracts.config.BrokerConnectionProperties;

/**
 * Собирает подключение к брокеру из его настроек. Подтверждения издателя и возвраты включены на каждом подключении:
 * брокер подтверждает и сообщение, которое никуда не маршрутизируется, и только возврат отличает доставку
 * от публикации в несуществующую очередь.
 */
final class BrokerConnectionFactories {

    /** Так подключение подписано в списке подключений брокера: администратор видит, какой сервис пришёл. */
    private static final String SERVICE_NAME = "contract-service";

    private BrokerConnectionFactories() {
    }

    static CachingConnectionFactory create(String brokerName, BrokerConnectionProperties properties) {
        RabbitConnectionFactoryBean clientFactory = clientFactory(properties);
        clientFactory.afterPropertiesSet();
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(
                clientFactory.getRabbitConnectionFactory());
        connectionFactory.setPublisherConfirmType(ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        connectionFactory.setConnectionNameStrategy(factory -> SERVICE_NAME + ":" + brokerName);
        return connectionFactory;
    }

    /** Клиентская фабрика до инициализации: TLS применяется к ней один раз при {@code afterPropertiesSet()}. */
    static RabbitConnectionFactoryBean clientFactory(BrokerConnectionProperties properties) {
        RabbitConnectionFactoryBean factory = new RabbitConnectionFactoryBean();
        factory.setHost(properties.host());
        factory.setPort(properties.port());
        factory.setVirtualHost(properties.virtualHost());
        factory.setUsername(properties.username());
        factory.setPassword(properties.password());
        factory.setConnectionTimeout((int) properties.connectionTimeout().toMillis());
        BrokerConnectionProperties.Tls tls = properties.tls();
        factory.setUseSSL(tls.enabled());
        factory.setSkipServerCertificateValidation(!tls.validateServerCertificate());
        factory.setEnableHostnameVerification(tls.verifyHostname());
        return factory;
    }
}
