package ru.projectx.contracts.service.integration.listener;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.projectx.contracts.config.broker.MarketingBrokerProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.service.integration.broker.MarketingBroker;
import ru.projectx.contracts.service.integration.consumer.AdditionalInboxQueue;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class OrderEventQueueConfig {

    @Bean
    AdditionalInboxQueue orderEventsQueue(MarketingBrokerProperties properties) {
        return new AdditionalInboxQueue(properties.orderEvents().queue(), properties.listenerAutoStartup());
    }

    @Bean
    AdditionalInboxEndpoint orderEventsEndpoint(MarketingBrokerProperties properties,
            @MarketingBroker SimpleRabbitListenerContainerFactory containerFactory, InboxQueueListener listener,
            OrderEventEnvelopes envelopes) {
        return new AdditionalInboxEndpoint(properties.orderEvents().queue(), containerFactory,
                listener.reading(envelopes));
    }
}
