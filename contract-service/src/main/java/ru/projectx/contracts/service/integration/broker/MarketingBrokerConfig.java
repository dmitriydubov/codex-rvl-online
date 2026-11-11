package ru.projectx.contracts.service.integration.broker;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.projectx.contracts.config.broker.MarketingBrokerProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class MarketingBrokerConfig {

    private static final String MARKETING = "marketing";
    private static final String HEALTH_KEY = "brokers";

    @Bean
    @MarketingBroker
    public CachingConnectionFactory marketingBrokerConnectionFactory(MarketingBrokerProperties properties) {
        return BrokerConnectionFactories.create(MARKETING, properties.connection());
    }

    @Bean
    @MarketingBroker
    public RabbitTemplate marketingBrokerTemplate(@MarketingBroker ConnectionFactory connectionFactory) {
        return BrokerTemplates.create(connectionFactory);
    }

    @Bean
    @MarketingBroker
    public SimpleRabbitListenerContainerFactory marketingBrokerListenerContainerFactory(
            @MarketingBroker ConnectionFactory connectionFactory) {
        return BrokerListenerContainerFactories.create(MARKETING, connectionFactory);
    }

    @Bean
    @ConditionalOnEnabledHealthIndicator(HEALTH_KEY)
    public HealthIndicator marketingBrokerHealthIndicator(@MarketingBroker ConnectionFactory connectionFactory) {
        return new BrokerHealthIndicator(MARKETING, connectionFactory);
    }

    @Bean
    AdditionalRoute documentFixesRoute(@MarketingBroker RabbitTemplate template, MarketingBrokerProperties properties) {
        MarketingBrokerProperties.DocumentFixes fixes = properties.documentFixes();
        return new AdditionalRoute(PaymentOrderEvents.PAYMENT_ORDER_FIXED,
                new OutboundRoute(template, fixes.exchange(), fixes.routingKey()));
    }
}
