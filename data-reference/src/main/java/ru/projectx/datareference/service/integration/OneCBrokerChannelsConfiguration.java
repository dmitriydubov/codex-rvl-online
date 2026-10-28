package ru.projectx.datareference.service.integration;

import org.springframework.boot.amqp.autoconfigure.ConnectionFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OneCBrokerChannelsConfiguration {

    @Bean
    ConnectionFactoryCustomizer replyTypeCheck() {
        return factory -> factory.setChannelShouldCheckRpcResponseType(true);
    }
}
