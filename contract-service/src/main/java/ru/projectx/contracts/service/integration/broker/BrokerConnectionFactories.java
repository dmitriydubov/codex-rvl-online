package ru.projectx.contracts.service.integration.broker;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import ru.projectx.contracts.config.broker.BrokerConnectionProperties;

final class BrokerConnectionFactories {

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
