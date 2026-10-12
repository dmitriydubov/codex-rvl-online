package ru.projectx.contracts.service.integration;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.projectx.contracts.config.InternalBrokerProperties;
import ru.projectx.contracts.config.OneCBrokerProperties;

/**
 * Два брокера — два набора бинов: межсервисный брокер контура и брокер 1С:УСК. Автонастройка AMQP выключена, чтобы
 * ни один бин не выбирал брокер по умолчанию: слушатель и публикатор называют свой брокер квалификатором.
 * Компоненты здоровья выключаются свойством {@code management.health.brokers.enabled} там, где брокера нет.
 */
@Configuration(proxyBeanMethods = false)
@EnableRabbit
public class BrokerConfig {

    static final String INTERNAL = "internal";
    static final String ONEC = "onec";
    private static final String HEALTH_KEY = "brokers";

    @Bean
    @InternalBroker
    public CachingConnectionFactory internalBrokerConnectionFactory(InternalBrokerProperties properties) {
        return BrokerConnectionFactories.create(INTERNAL, properties.connection());
    }

    @Bean
    @InternalBroker
    public RabbitTemplate internalBrokerTemplate(@InternalBroker ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }

    @Bean
    @InternalBroker
    public SimpleRabbitListenerContainerFactory internalBrokerListenerContainerFactory(
            @InternalBroker ConnectionFactory connectionFactory, InternalBrokerProperties properties) {
        return BrokerListenerContainerFactories.create(INTERNAL, connectionFactory, properties.listenerAutoStartup());
    }

    @Bean
    @ConditionalOnEnabledHealthIndicator(HEALTH_KEY)
    public HealthIndicator internalBrokerHealthIndicator(@InternalBroker ConnectionFactory connectionFactory) {
        return new BrokerHealthIndicator(INTERNAL, connectionFactory);
    }

    @Bean
    @OneCBroker
    public CachingConnectionFactory onecBrokerConnectionFactory(OneCBrokerProperties properties) {
        return BrokerConnectionFactories.create(ONEC, properties.connection());
    }

    @Bean
    @OneCBroker
    public RabbitTemplate onecBrokerTemplate(@OneCBroker ConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }

    @Bean
    @OneCBroker
    public SimpleRabbitListenerContainerFactory onecBrokerListenerContainerFactory(
            @OneCBroker ConnectionFactory connectionFactory, OneCBrokerProperties properties) {
        return BrokerListenerContainerFactories.create(ONEC, connectionFactory, properties.listenerAutoStartup());
    }

    @Bean
    @ConditionalOnEnabledHealthIndicator(HEALTH_KEY)
    public HealthIndicator onecBrokerHealthIndicator(@OneCBroker ConnectionFactory connectionFactory) {
        return new BrokerHealthIndicator(ONEC, connectionFactory);
    }
}
