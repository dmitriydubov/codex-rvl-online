package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class BrokerTemplatesTest {

    private final CachingConnectionFactory connectionFactory = new CachingConnectionFactory("broker.example");
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private final Logger log = (Logger) LoggerFactory.getLogger(BrokerTemplates.class);

    @BeforeEach
    void collectJournal() {
        journal.start();
        log.addAppender(journal);
    }

    @AfterEach
    void releaseConnectionFactory() {
        log.detachAppender(journal);
        connectionFactory.destroy();
    }

    @Test
    void template_publishesEveryMessageAsMandatoryOnItsConnection() {
        RabbitTemplate template = BrokerTemplates.create(connectionFactory);

        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(template.isMandatoryFor(new Message(new byte[0], new MessageProperties()))).isTrue();
    }

    @Test
    void returnedMessage_isLoggedAsWarningWithoutItsBody() {
        RabbitTemplate template = BrokerTemplates.create(connectionFactory);
        RabbitTemplate.ReturnsCallback callback =
                (RabbitTemplate.ReturnsCallback) ReflectionTestUtils.getField(template, "returnsCallback");
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442");
        Message message = new Message("{\"last_name\": \"Тестова\"}".getBytes(StandardCharsets.UTF_8), properties);

        callback.returnedMessage(new ReturnedMessage(message, 312, "NO_ROUTE", "", "status-changes"));

        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("брокер вернул сообщение 7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442 из очереди status-changes: "
                            + "NO_ROUTE (312)")
                    .doesNotContain("Тестова");
        });
    }
}
