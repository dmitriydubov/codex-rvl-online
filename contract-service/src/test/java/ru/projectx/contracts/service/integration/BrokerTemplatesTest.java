package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Шаблон публикации отправляет сообщения как mandatory на своём подключении: без этого публикация в несуществующую
 * очередь получила бы подтверждение брокера и выглядела бы доставкой.
 */
class BrokerTemplatesTest {

    private final CachingConnectionFactory connectionFactory = new CachingConnectionFactory("broker.example");

    @AfterEach
    void releaseConnectionFactory() {
        connectionFactory.destroy();
    }

    @Test
    void template_publishesEveryMessageAsMandatoryOnItsConnection() {
        RabbitTemplate template = BrokerTemplates.create(connectionFactory);

        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(template.isMandatoryFor(new Message(new byte[0], new MessageProperties()))).isTrue();
    }
}
